package com.example.ai

import android.util.Log
import com.example.audio.AudioStreamManager
import com.example.core.MarcoConfig
import com.example.domain.manager.ConversationManager
import com.example.domain.manager.PermissionManager
import com.example.domain.model.ConnectionState
import com.example.domain.model.MarcoSettings
import com.example.domain.model.OrbState
import com.example.tools.ToolDeclarations
import com.example.tools.ToolExecutionEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.ContextWindowCompressionConfig
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.LiveSessionResumptionUpdate
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.SessionResumptionConfig
import com.google.firebase.ai.type.SlidingWindow
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

@OptIn(PublicPreviewAPI::class)
class LiveSessionManager(
    private val audioStreamManager: AudioStreamManager,
    private val toolExecutionEngine: ToolExecutionEngine,
    private val conversationManager: ConversationManager,
    private val permissionManager: PermissionManager
) {
    companion object {
        private const val TAG = "LiveSessionManager"
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }

    private data class HandshakeProfile(
        val label: String,
        val modelName: String,
        val includeVoice: Boolean,
        val includeTranscriptions: Boolean,
        val includeCompressionAndVad: Boolean,
        val includeTools: Boolean,
        val requestSessionResumption: Boolean
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionMutex = Mutex()

    private var liveSession: LiveSession? = null
    private var receiveJob: Job? = null
    private var recoveryJob: Job? = null
    private var resumptionHandle: String? = null
    private var shouldStayConnected = false
    private var recoveryAttempts = 0
    private var latestSettings = MarcoSettings()

    private val cancelledToolCallIds = ConcurrentHashMap.newKeySet<String>()

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    private val _activeToolName = MutableStateFlow<String?>(null)
    val activeToolName: StateFlow<String?> = _activeToolName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun startLiveSession(
        settings: MarcoSettings,
        greetUser: Boolean = false,
        initialPrompt: String? = null
    ) {
        if (!permissionManager.hasRecordAudio()) {
            _errorMessage.value = MarcoConfig.MISSING_PERMISSION_LINE
            _connectionState.value = ConnectionState.ERROR
            _orbState.value = OrbState.IDLE
            return
        }

        latestSettings = settings
        shouldStayConnected = true
        recoveryAttempts = 0
        recoveryJob?.cancel()
        recoveryJob = null
        _errorMessage.value = null

        connectInternal(
            settings = settings,
            isRecovery = false,
            greetUser = greetUser,
            initialPrompt = initialPrompt
        )
    }

    suspend fun stopLiveSession() {
        shouldStayConnected = false
        recoveryJob?.cancel()
        recoveryJob = null
        closeInternal()
        _connectionState.value = ConnectionState.OFFLINE
        _orbState.value = OrbState.IDLE
        _activeToolName.value = null
        conversationManager.clearLiveTranscripts()
    }

    suspend fun sendTextTurn(prompt: String) {
        val session = liveSession ?: return
        if (session.isClosed()) return
        try {
            audioStreamManager.interruptPlayback()
            _orbState.value = OrbState.THINKING
            session.send(prompt, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text turn", e)
            _errorMessage.value = e.localizedMessage ?: "Failed to send voice command."
        }
    }

    private fun buildHandshakeProfiles(selectedModel: String): List<HandshakeProfile> {
        val alternateModel = MarcoConfig.LIVE_MODELS
            .firstOrNull { it.id != selectedModel }
            ?.id
            ?: "gemini-3.1-flash-live-preview"

        return listOf(
            HandshakeProfile(
                label = "full",
                modelName = selectedModel,
                includeVoice = true,
                includeTranscriptions = true,
                includeCompressionAndVad = true,
                includeTools = true,
                requestSessionResumption = true
            ),
            HandshakeProfile(
                label = "core-tools",
                modelName = selectedModel,
                includeVoice = true,
                includeTranscriptions = true,
                includeCompressionAndVad = false,
                includeTools = true,
                requestSessionResumption = false
            ),
            HandshakeProfile(
                label = "alt-model-tools",
                modelName = alternateModel,
                includeVoice = true,
                includeTranscriptions = true,
                includeCompressionAndVad = false,
                includeTools = true,
                requestSessionResumption = false
            ),
            HandshakeProfile(
                label = "minimal-audio",
                modelName = alternateModel,
                includeVoice = false,
                includeTranscriptions = false,
                includeCompressionAndVad = false,
                includeTools = false,
                requestSessionResumption = false
            )
        )
    }

    private suspend fun connectWithProfile(
        profile: HandshakeProfile,
        settings: MarcoSettings,
        currentHandle: String?
    ): LiveSession {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
            modelName = profile.modelName,
            generationConfig = liveGenerationConfig {
                responseModality = ResponseModality.AUDIO
                if (profile.includeVoice) {
                    speechConfig = SpeechConfig(voice = Voice(settings.selectedVoice))
                }
                if (profile.includeTranscriptions) {
                    inputAudioTranscription = AudioTranscriptionConfig()
                    outputAudioTranscription = AudioTranscriptionConfig()
                }
                if (profile.includeCompressionAndVad) {
                    contextWindowCompression = ContextWindowCompressionConfig(
                        triggerTokens = 12_000,
                        slidingWindow = SlidingWindow(targetTokens = 4_000)
                    )
                    realtimeInputConfig = RealtimeInputConfig.builder()
                        .setActivityHandling(RealtimeInputConfig.ActivityHandling.INTERRUPT)
                        .setTurnCoverage(RealtimeInputConfig.TurnCoverage.ONLY_ACTIVITY)
                        .build()
                }
            },
            tools = if (profile.includeTools) ToolDeclarations.toolsList else emptyList(),
            systemInstruction = content {
                text(MarcoConfig.buildSystemInstruction())
            }
        )

        return when {
            currentHandle != null && profile.requestSessionResumption ->
                model.connect(SessionResumptionConfig(currentHandle))
            profile.requestSessionResumption ->
                model.connect(SessionResumptionConfig())
            else ->
                model.connect()
        }
    }

    private suspend fun connectInternal(
        settings: MarcoSettings,
        isRecovery: Boolean,
        greetUser: Boolean,
        initialPrompt: String?
    ) = sessionMutex.withLock {
        try {
            closeInternalLocked()
            if (!shouldStayConnected) return@withLock

            _connectionState.value = if (isRecovery) ConnectionState.RECOVERING else ConnectionState.CONNECTING
            _orbState.value = OrbState.THINKING

            val currentHandle = resumptionHandle
            val profiles = buildHandshakeProfiles(settings.selectedModel)
            var session: LiveSession? = null
            var connectedProfile: HandshakeProfile? = null
            var lastHandshakeError: Exception? = null

            for ((index, profile) in profiles.withIndex()) {
                if (!shouldStayConnected) return@withLock
                try {
                    session = connectWithProfile(profile, settings, currentHandle)
                    connectedProfile = profile
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ServiceConnectionHandshakeFailedException) {
                    lastHandshakeError = e
                    Log.w(
                        TAG,
                        "Live handshake rejected on profile='${profile.label}' model='${profile.modelName}': ${e.message}",
                        e.cause
                    )
                    if (index < profiles.lastIndex) {
                        delay(180L)
                    }
                }
            }

            val activeSession = session ?: throw (lastHandshakeError
                ?: IllegalStateException("Unable to establish Gemini Live session."))

            liveSession = activeSession
            recoveryAttempts = 0
            _connectionState.value = ConnectionState.LIVE
            _orbState.value = OrbState.LISTENING

            audioStreamManager.setMuted(settings.isMuted)
            audioStreamManager.startPlayback()

            val micStarted = audioStreamManager.startCapture { pcmFrame ->
                val current = liveSession
                if (current != null && !current.isClosed() && shouldStayConnected) {
                    try {
                        current.sendAudioRealtime(
                            InlineData(pcmFrame, "audio/pcm;rate=${AudioStreamManager.INPUT_SAMPLE_RATE}")
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "sendAudioRealtime frame drop: ${e.message}")
                    }
                }
            }

            if (!micStarted) {
                _errorMessage.value = MarcoConfig.MISSING_PERMISSION_LINE
            }

            startReceivingLoop(activeSession)

            if (!isRecovery) {
                when {
                    !initialPrompt.isNullOrBlank() -> {
                        activeSession.send(initialPrompt, true)
                    }
                    greetUser -> {
                        activeSession.send(
                            "Greet Sanni now using your signature introduction line.",
                            true
                        )
                    }
                }
            } else if (resumptionHandle == null) {
                // Restore conversational context if server handle wasn't available
                val summary = conversationManager.buildRecentContextSummary()
                if (summary.isNotEmpty()) {
                    activeSession.send(
                        content("user") {
                            text("Context from earlier in our session:\n$summary")
                        },
                        false
                    )
                }
            }

            Log.i(
                TAG,
                "Gemini Live session connected (profile=${connectedProfile?.label}, model=${connectedProfile?.modelName}, voice=${settings.selectedVoice})"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ServiceConnectionHandshakeFailedException) {
            Log.e(TAG, "All Gemini Live handshake profiles failed: ${e.message}", e)
            shouldStayConnected = false
            _errorMessage.value =
                "Live server closed the connection. Check network/App Check or tap the orb to retry."
            _connectionState.value = ConnectionState.ERROR
            _orbState.value = OrbState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect Gemini Live session", e)
            _errorMessage.value = e.localizedMessage ?: "Unable to connect to Marco Live."
            _connectionState.value = ConnectionState.ERROR
            _orbState.value = OrbState.IDLE
            if (shouldStayConnected) {
                scheduleRecovery()
            }
        }
    }

    private fun startReceivingLoop(session: LiveSession) {
        receiveJob?.cancel()
        receiveJob = scope.launch {
            try {
                session.receive().collect { message ->
                    when (message) {
                        is LiveServerContent -> handleServerContent(message)
                        is LiveServerToolCall -> handleToolCall(session, message)
                        is LiveServerToolCallCancellation -> {
                            cancelledToolCallIds.addAll(message.functionIds)
                            _activeToolName.value = null
                        }
                        is LiveSessionResumptionUpdate -> {
                            if (message.resumable == true && !message.newHandle.isNullOrBlank()) {
                                resumptionHandle = message.newHandle
                            }
                        }
                        is LiveServerGoAway -> {
                            Log.i(TAG, "Received LiveServerGoAway; proactively resuming session")
                            val handle = resumptionHandle
                            if (handle != null && shouldStayConnected) {
                                try {
                                    session.resumeSession(SessionResumptionConfig(handle))
                                } catch (e: Exception) {
                                    Log.w(TAG, "resumeSession failed on GoAway; reconnecting", e)
                                    scheduleRecovery()
                                }
                            }
                        }
                    }
                }
                if (shouldStayConnected && isActive) {
                    Log.w(TAG, "LiveSession receive stream ended unexpectedly; attempting recovery")
                    scheduleRecovery()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in LiveSession receive stream", e)
                if (shouldStayConnected) {
                    _errorMessage.value = e.localizedMessage ?: "Live connection interrupted."
                    scheduleRecovery()
                }
            }
        }
    }

    private fun handleServerContent(message: LiveServerContent) {
        if (message.interrupted) {
            Log.d(TAG, "Barge-in interruption detected: flushing audio playback")
            audioStreamManager.interruptPlayback()
            conversationManager.commitTurnIfPresent()
            _orbState.value = OrbState.LISTENING
            return
        }

        message.inputTranscription?.text?.let { delta ->
            conversationManager.appendUserTranscriptDelta(delta)
            if (!audioStreamManager.isSpeakingOutput.value) {
                _orbState.value = OrbState.LISTENING
            }
        }

        message.outputTranscription?.text?.let { delta ->
            conversationManager.appendMarcoTranscriptDelta(delta)
        }

        var playedAudio = false
        message.content?.parts?.forEach { part ->
            if (part is InlineDataPart && part.mimeType.startsWith("audio/pcm")) {
                audioStreamManager.enqueueOutputAudio(part.inlineData)
                playedAudio = true
            }
        }

        if (playedAudio) {
            _orbState.value = OrbState.SPEAKING
        }

        if (message.turnComplete || message.generationComplete) {
            conversationManager.commitTurnIfPresent()
            if (!audioStreamManager.isSpeakingOutput.value) {
                _orbState.value = OrbState.LISTENING
            }
        }
    }

    private suspend fun handleToolCall(session: LiveSession, toolCall: LiveServerToolCall) {
        _orbState.value = OrbState.THINKING
        val responses = mutableListOf<FunctionResponsePart>()

        for (call in toolCall.functionCalls) {
            val callId = call.id
            if (callId != null && cancelledToolCallIds.remove(callId)) {
                continue
            }
            _activeToolName.value = call.name
            val response = toolExecutionEngine.execute(call)
            if (callId != null && cancelledToolCallIds.remove(callId)) {
                continue
            }
            responses.add(response)
        }

        _activeToolName.value = null
        if (responses.isNotEmpty() && !session.isClosed()) {
            try {
                session.sendFunctionResponse(responses)
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending function response", e)
            }
        }
    }

    private fun scheduleRecovery() {
        if (!shouldStayConnected) return
        if (recoveryJob?.isActive == true) return
        if (recoveryAttempts >= MAX_RECOVERY_ATTEMPTS) {
            _connectionState.value = ConnectionState.ERROR
            _orbState.value = OrbState.IDLE
            _errorMessage.value = "Connection lost. Tap the orb to reconnect Marco."
            return
        }

        recoveryAttempts++
        recoveryJob = scope.launch {
            _connectionState.value = ConnectionState.RECOVERING
            _orbState.value = OrbState.THINKING
            delay(1200L * recoveryAttempts)
            if (shouldStayConnected && isActive) {
                connectInternal(
                    settings = latestSettings,
                    isRecovery = true,
                    greetUser = false,
                    initialPrompt = null
                )
            }
        }
    }

    private suspend fun closeInternal() = sessionMutex.withLock {
        closeInternalLocked()
    }

    private suspend fun closeInternalLocked() {
        receiveJob?.cancel()
        receiveJob = null
        audioStreamManager.stopCapture()
        audioStreamManager.stopPlayback()
        try {
            liveSession?.stopReceiving()
            liveSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing LiveSession", e)
        } finally {
            liveSession = null
        }
        cancelledToolCallIds.clear()
    }
}
