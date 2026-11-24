package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

class AudioStreamManager {

    companion object {
        private const val TAG = "AudioStreamManager"
        const val INPUT_SAMPLE_RATE = 16_000
        const val OUTPUT_SAMPLE_RATE = 24_000
        private const val INPUT_FRAME_BYTES = 1024 // 32ms at 16kHz mono PCM16
        private const val SPECTRUM_BANDS = 24
    }

    private val audioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var playbackChannel = Channel<ByteArray>(capacity = 256)

    private val isCapturingFlag = AtomicBoolean(false)
    private val isMutedFlag = AtomicBoolean(false)
    private val isSpeakerActiveFlag = AtomicBoolean(false)
    private val playbackLock = Any()

    @Volatile
    private var lastPlaybackWriteTimeMs = 0L

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _speakerLevel = MutableStateFlow(0f)
    val speakerLevel: StateFlow<Float> = _speakerLevel.asStateFlow()

    private val _isSpeakingOutput = MutableStateFlow(false)
    val isSpeakingOutput: StateFlow<Boolean> = _isSpeakingOutput.asStateFlow()

    private val _waveformBands = MutableStateFlow(List(SPECTRUM_BANDS) { 0.08f })
    val waveformBands: StateFlow<List<Float>> = _waveformBands.asStateFlow()

    fun setMuted(muted: Boolean) {
        isMutedFlag.set(muted)
        if (muted) {
            _micLevel.value = 0f
            if (!isSpeakerActiveFlag.get()) {
                _waveformBands.value = List(SPECTRUM_BANDS) { 0.08f }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startCapture(onAudioFrame: suspend (ByteArray) -> Unit): Boolean {
        if (isCapturingFlag.get()) return true

        val minBuf = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(INPUT_FRAME_BYTES * 4)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return false
        }

        audioRecord = record
        attachAudioEffects(record.audioSessionId)

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord startRecording failed", e)
            releaseRecorder()
            return false
        }

        isCapturingFlag.set(true)
        captureJob = audioScope.launch {
            val buffer = ByteArray(INPUT_FRAME_BYTES)
            while (isActive && isCapturingFlag.get()) {
                val readBytes = record.read(buffer, 0, buffer.size)
                if (readBytes > 0) {
                    if (isMutedFlag.get()) {
                        _micLevel.value = 0f
                        continue
                    }
                    val frame = buffer.copyOf(readBytes)
                    val level = computeRmsNormalized(frame, readBytes)
                    _micLevel.value = level
                    if (!isSpeakerActiveFlag.get()) {
                        _waveformBands.value = computeBands(frame, readBytes, level)
                    }
                    onAudioFrame(frame)
                } else if (readBytes < 0) {
                    Log.w(TAG, "AudioRecord read error: $readBytes")
                    delay(25)
                }
            }
        }
        Log.d(TAG, "Audio capture started at ${INPUT_SAMPLE_RATE}Hz PCM16")
        return true
    }

    fun stopCapture() {
        isCapturingFlag.set(false)
        captureJob?.cancel()
        captureJob = null
        releaseRecorder()
        _micLevel.value = 0f
        if (!isSpeakerActiveFlag.get()) {
            _waveformBands.value = List(SPECTRUM_BANDS) { 0.08f }
        }
    }

    fun startPlayback() {
        synchronized(playbackLock) {
            if (audioTrack != null) return
            val minBuf = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()
        }

        playbackJob?.cancel()
        playbackJob = audioScope.launch {
            while (isActive) {
                val chunk = playbackChannel.receiveCatching().getOrNull() ?: break
                val track = synchronized(playbackLock) { audioTrack } ?: continue
                if (chunk.isEmpty()) continue

                isSpeakerActiveFlag.set(true)
                _isSpeakingOutput.value = true
                lastPlaybackWriteTimeMs = System.currentTimeMillis()

                val level = computeRmsNormalized(chunk, chunk.size)
                _speakerLevel.value = level
                _waveformBands.value = computeBands(chunk, chunk.size, level)

                var offset = 0
                while (offset < chunk.size && isActive) {
                    val written = synchronized(playbackLock) {
                        audioTrack?.write(chunk, offset, chunk.size - offset) ?: -1
                    }
                    if (written <= 0) break
                    offset += written
                }

                // Check if queue went quiet to smoothly settle speaking indicator
                if (playbackChannel.isEmpty) {
                    delay(140)
                    if (playbackChannel.isEmpty &&
                        System.currentTimeMillis() - lastPlaybackWriteTimeMs >= 130
                    ) {
                        isSpeakerActiveFlag.set(false)
                        _isSpeakingOutput.value = false
                        _speakerLevel.value = 0f
                    }
                }
            }
        }
        Log.d(TAG, "AudioTrack playback started at ${OUTPUT_SAMPLE_RATE}Hz PCM16")
    }

    fun enqueueOutputAudio(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        if (audioTrack == null) {
            startPlayback()
        }
        val result = playbackChannel.trySend(pcmBytes)
        if (result.isFailure) {
            // Drop oldest frame if saturated to preserve low latency
            playbackChannel.tryReceive()
            playbackChannel.trySend(pcmBytes)
        }
    }

    fun interruptPlayback() {
        while (playbackChannel.tryReceive().isSuccess) {
            // Drain queued audio chunks immediately on barge-in
        }
        synchronized(playbackLock) {
            audioTrack?.let { track ->
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                    }
                    track.flush()
                    track.play()
                } catch (e: Exception) {
                    Log.w(TAG, "Error flushing AudioTrack during interruption", e)
                }
            }
        }
        isSpeakerActiveFlag.set(false)
        _isSpeakingOutput.value = false
        _speakerLevel.value = 0f
    }

    fun stopPlayback() {
        interruptPlayback()
        playbackJob?.cancel()
        playbackJob = null
        synchronized(playbackLock) {
            audioTrack?.let { track ->
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Error releasing AudioTrack", e)
                }
            }
            audioTrack = null
        }
    }

    fun releaseAll() {
        stopCapture()
        stopPlayback()
    }

    private fun attachAudioEffects(sessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                }
            }
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach AEC/NS effects", e)
        }
    }

    private fun releaseRecorder() {
        try {
            echoCanceler?.release()
        } catch (_: Exception) {
        } finally {
            echoCanceler = null
        }
        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {
        } finally {
            noiseSuppressor = null
        }
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    private fun computeRmsNormalized(pcm: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < length) {
            val lo = pcm[i].toInt() and 0xFF
            val hi = pcm[i + 1].toInt()
            val sample = (hi shl 8) or lo
            val norm = sample / 32768.0
            sumSq += norm * norm
            count++
            i += 2
        }
        if (count == 0) return 0f
        val rms = sqrt(sumSq / count).toFloat()
        return (rms * 3.8f).coerceIn(0f, 1f)
    }

    private fun computeBands(pcm: ByteArray, length: Int, overallLevel: Float): List<Float> {
        val sampleCount = length / 2
        if (sampleCount < SPECTRUM_BANDS) {
            return List(SPECTRUM_BANDS) { (overallLevel * 0.6f + 0.08f).coerceIn(0.08f, 1f) }
        }
        val step = (sampleCount / SPECTRUM_BANDS).coerceAtLeast(1)
        return List(SPECTRUM_BANDS) { band ->
            val startSample = band * step
            val endSample = ((band + 1) * step).coerceAtMost(sampleCount)
            var peak = 0f
            for (s in startSample until endSample) {
                val idx = s * 2
                val lo = pcm[idx].toInt() and 0xFF
                val hi = pcm[idx + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort().toInt()
                val mag = abs(sample) / 32768f
                if (mag > peak) peak = mag
            }
            (0.08f + (peak * 0.7f + overallLevel * 0.45f)).coerceIn(0.08f, 1f)
        }
    }
}
