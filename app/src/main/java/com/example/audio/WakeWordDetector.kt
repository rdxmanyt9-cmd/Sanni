package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class WakeWordDetector(
    private val context: Context
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        private val WAKE_PHRASES = listOf(
            "hey marco",
            "ok marco",
            "hi marco",
            "hello marco",
            "marco",
            "hey marko",
            "marko",
            "hey margo",
            "margo",
            "hey marcus",
            "marcus",
            "मार्को",
            "हे मार्को"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val isRunning = AtomicBoolean(false)
    private var sensitivity: Float = 0.65f
    private var onWakeDetected: (() -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private var acousticFallbackJob: Job? = null
    private var useSpeechRecognizer = false

    private val _isListeningForWakeWord = MutableStateFlow(false)
    val isListeningForWakeWord: StateFlow<Boolean> = _isListeningForWakeWord.asStateFlow()

    private val _standbyMicLevel = MutableStateFlow(0f)
    val standbyMicLevel: StateFlow<Float> = _standbyMicLevel.asStateFlow()

    fun updateSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.2f, 1.0f)
    }

    fun start(onWake: () -> Unit) {
        onWakeDetected = onWake
        if (isRunning.getAndSet(true)) return
        _isListeningForWakeWord.value = true

        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val recognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        useSpeechRecognizer = onDeviceAvailable || recognizerAvailable

        if (useSpeechRecognizer) {
            Log.d(TAG, "Starting local wake-word listener via SpeechRecognizer (onDevice=$onDeviceAvailable)")
            mainHandler.post { startSpeechRecognizerLoop(onDeviceAvailable) }
        } else {
            Log.d(TAG, "SpeechRecognizer unavailable; starting AudioRecord acoustic wake-word detector")
            startAcousticWakeDetector()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        _isListeningForWakeWord.value = false
        _standbyMicLevel.value = 0f
        acousticFallbackJob?.cancel()
        acousticFallbackJob = null
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying SpeechRecognizer", e)
            } finally {
                speechRecognizer = null
            }
        }
        Log.d(TAG, "WakeWordDetector stopped")
    }

    private fun startSpeechRecognizerLoop(preferOnDevice: Boolean) {
        if (!isRunning.get()) return
        try {
            speechRecognizer?.destroy()
            speechRecognizer = if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    _standbyMicLevel.value = norm
                }

                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    _standbyMicLevel.value = 0f
                    if (!isRunning.get()) return
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        Log.w(TAG, "SpeechRecognizer missing permission; stopping wake listener")
                        stop()
                        return
                    }
                    // If on-device recognizer fails repeatedly on an emulator without speech engine, fall back
                    if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER) {
                        Log.w(TAG, "SpeechRecognizer error=$error, switching to AudioRecord VAD fallback")
                        startAcousticWakeDetector()
                        return
                    }
                    val restartDelay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 550L else 180L
                    mainHandler.postDelayed({ startListeningOnce() }, restartDelay)
                }

                override fun onResults(results: Bundle?) {
                    handleRecognitionBundle(results, isFinal = true)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    handleRecognitionBundle(partialResults, isFinal = false)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListeningOnce()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start SpeechRecognizer, falling back to AudioRecord VAD", e)
            startAcousticWakeDetector()
        }
    }

    private fun startListeningOnce() {
        if (!isRunning.get()) return
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
        }
    }

    private fun handleRecognitionBundle(bundle: Bundle?, isFinal: Boolean) {
        if (!isRunning.get()) return
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        val detected = matches.any { text ->
            val normalized = text.lowercase().trim()
            WAKE_PHRASES.any { phrase -> normalized.contains(phrase) }
        }
        if (detected) {
            Log.i(TAG, "Wake word detected in speech: $matches")
            stop()
            onWakeDetected?.invoke()
            return
        }
        if (isFinal && isRunning.get()) {
            mainHandler.postDelayed({ startListeningOnce() }, 150L)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAcousticWakeDetector() {
        if (acousticFallbackJob?.isActive == true) return
        acousticFallbackJob = scope.launch {
            val sampleRate = 16_000
            val frameSize = 1024
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(frameSize * 4)

            val record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            } catch (e: Exception) {
                Log.e(TAG, "Acoustic wake AudioRecord creation failed", e)
                return@launch
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@launch
            }

            try {
                record.startRecording()
                val buffer = ByteArray(frameSize)
                var voicedFrames = 0
                var silentFrames = 0
                val threshold = 0.28f - (sensitivity * 0.16f) // higher sensitivity -> lower RMS threshold

                while (isActive && isRunning.get()) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val rms = computeRms(buffer, read)
                        _standbyMicLevel.value = (rms * 3f).coerceIn(0f, 1f)
                        if (rms >= threshold) {
                            voicedFrames++
                            silentFrames = 0
                            // Sustained vocal phrase (~500ms-700ms) triggers wake fallback
                            if (voicedFrames >= 18) {
                                Log.i(TAG, "Acoustic voice wake impulse detected")
                                stop()
                                mainHandler.post { onWakeDetected?.invoke() }
                                break
                            }
                        } else {
                            silentFrames++
                            if (silentFrames > 6) voicedFrames = 0
                        }
                    } else {
                        delay(30)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Acoustic wake loop interrupted", e)
            } finally {
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                    record.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun computeRms(pcm: ByteArray, length: Int): Float {
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
        return if (count > 0) sqrt(sumSq / count).toFloat() else 0f
    }
}
