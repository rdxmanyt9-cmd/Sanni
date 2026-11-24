package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.MarcoConfig
import com.example.domain.model.ConnectionState
import com.example.domain.model.MarcoSettings
import com.example.domain.model.MarcoUiState
import com.example.domain.model.OrbState
import com.example.domain.model.PendingConfirmation
import com.example.service.BackgroundAudioService
import com.example.service.MarcoRuntime
import com.example.service.NotificationMemoryStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarcoViewModel(application: Application) : AndroidViewModel(application) {

    private val runtime = MarcoRuntime.get(application)
    private val settingsRepo = runtime.settingsRepository
    private val permissionManager = runtime.permissionManager
    private val conversationManager = runtime.conversationManager
    private val audioStreamManager = runtime.audioStreamManager
    private val liveSessionManager = runtime.liveSessionManager
    private val wakeWordDetector = runtime.wakeWordDetector
    private val toolExecutionEngine = runtime.toolExecutionEngine

    private val _systemRefreshTick = MutableStateFlow(0)
    private val _transientBanner = MutableStateFlow<String?>(null)

    val waveformBands: StateFlow<List<Float>> = audioStreamManager.waveformBands

    private val connectionAndOrbFlow = combine(
        liveSessionManager.connectionState,
        liveSessionManager.orbState,
        runtime.isStandbyActive,
        audioStreamManager.isSpeakingOutput
    ) { conn, orb, standby, speaking ->
        ConnectionOrbTuple(conn, orb, standby, speaking)
    }

    private val audioLevelsFlow = combine(
        audioStreamManager.micLevel,
        audioStreamManager.speakerLevel,
        wakeWordDetector.standbyMicLevel
    ) { mic, spk, standbyMic ->
        AudioLevelsTuple(mic, spk, standbyMic)
    }

    private val baseStatusFlow = combine(
        connectionAndOrbFlow,
        audioLevelsFlow
    ) { connOrb, levels ->
        AudioStatusSnapshot(
            conn = connOrb.conn,
            orb = connOrb.orb,
            standby = connOrb.standby,
            speaking = connOrb.speaking,
            micLevel = levels.mic,
            speakerLevel = levels.spk,
            standbyMic = levels.standbyMic
        )
    }

    private val transcriptCaptionFlow = combine(
        conversationManager.liveUserTranscript,
        conversationManager.liveMarcoTranscript,
        liveSessionManager.activeToolName
    ) { userText, marcoText, toolName ->
        Triple(userText, marcoText, toolName)
    }

    private val alertsFlow = combine(
        liveSessionManager.errorMessage,
        conversationManager.pendingConfirmation,
        _transientBanner
    ) { error, confirmation, banner ->
        Triple(error, confirmation, banner)
    }

    private val captionFlow = combine(
        transcriptCaptionFlow,
        alertsFlow
    ) { transcripts, alerts ->
        CaptionSnapshot(
            userText = transcripts.first,
            marcoText = transcripts.second,
            toolName = transcripts.third,
            error = alerts.first,
            confirmation = alerts.second,
            banner = alerts.third
        )
    }

    val uiState: StateFlow<MarcoUiState> = combine(
        baseStatusFlow,
        captionFlow,
        settingsRepo.settingsFlow,
        _systemRefreshTick
    ) { audio, captions, settings, _ ->
        NotificationMemoryStore.setConsentEnabled(
            settings.notificationReaderConsent && permissionManager.isNotificationAccessGranted()
        )

        val resolvedConnection = when {
            !settings.assistantEnabled -> ConnectionState.OFFLINE
            audio.conn == ConnectionState.LIVE ||
                audio.conn == ConnectionState.CONNECTING ||
                audio.conn == ConnectionState.RECOVERING -> audio.conn
            audio.standby -> ConnectionState.STANDBY_WAKEWORD
            audio.conn == ConnectionState.ERROR -> ConnectionState.ERROR
            else -> ConnectionState.OFFLINE
        }

        val resolvedOrb = when {
            !settings.assistantEnabled -> OrbState.IDLE
            resolvedConnection == ConnectionState.STANDBY_WAKEWORD -> OrbState.STANDBY
            audio.conn == ConnectionState.CONNECTING || audio.conn == ConnectionState.RECOVERING -> OrbState.THINKING
            captions.toolName != null -> OrbState.THINKING
            audio.speaking || audio.orb == OrbState.SPEAKING -> OrbState.SPEAKING
            resolvedConnection == ConnectionState.LIVE && !settings.isMuted -> OrbState.LISTENING
            else -> OrbState.IDLE
        }

        val isMicLive = (resolvedConnection == ConnectionState.LIVE && !settings.isMuted) ||
            resolvedConnection == ConnectionState.STANDBY_WAKEWORD

        MarcoUiState(
            orbState = resolvedOrb,
            connectionState = resolvedConnection,
            micLevel = if (resolvedConnection == ConnectionState.STANDBY_WAKEWORD) audio.standbyMic else audio.micLevel,
            speakerLevel = audio.speakerLevel,
            isMicActive = isMicLive,
            isWakeWordListening = audio.standby,
            liveInputCaption = captions.userText,
            liveMarcoCaption = captions.marcoText,
            statusBanner = captions.banner,
            errorMessage = captions.error,
            activeToolName = captions.toolName,
            pendingConfirmation = captions.confirmation,
            permissions = permissionManager.getAllPermissionStatuses(),
            isNotificationAccessGranted = permissionManager.isNotificationAccessGranted(),
            isBatteryOptimizationIgnored = permissionManager.isIgnoringBatteryOptimizations(),
            settings = settings,
            isInitialized = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MarcoUiState(
            permissions = permissionManager.getAllPermissionStatuses(),
            isNotificationAccessGranted = permissionManager.isNotificationAccessGranted(),
            isBatteryOptimizationIgnored = permissionManager.isIgnoringBatteryOptimizations()
        )
    )

    init {
        viewModelScope.launch {
            val initial = settingsRepo.settingsFlow.first()
            if (initial.onboardingCompleted && initial.assistantEnabled) {
                syncBackgroundAndWakeState(initial)
            }
        }
    }

    fun refreshSystemStatus() {
        _systemRefreshTick.value++
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepo.setOnboardingCompleted(true)
            val settings = settingsRepo.settingsFlow.first()
            syncBackgroundAndWakeState(settings)
        }
    }

    fun onOrbTapped() {
        viewModelScope.launch {
            val state = uiState.value
            if (!permissionManager.hasRecordAudio()) {
                _transientBanner.value = MarcoConfig.MISSING_PERMISSION_LINE
                return@launch
            }
            if (!state.settings.assistantEnabled) {
                settingsRepo.setAssistantEnabled(true)
            }
            when (state.connectionState) {
                ConnectionState.LIVE -> {
                    if (state.orbState == OrbState.SPEAKING) {
                        audioStreamManager.interruptPlayback()
                    } else {
                        runtime.pauseToStandbyOrOffline()
                    }
                }
                ConnectionState.CONNECTING, ConnectionState.RECOVERING -> {
                    runtime.pauseToStandbyOrOffline()
                }
                ConnectionState.STANDBY_WAKEWORD, ConnectionState.OFFLINE, ConnectionState.ERROR -> {
                    _transientBanner.value = null
                    val firstGreet = conversationManager.turns.value.isEmpty()
                    runtime.activateLiveSession(greetUser = firstGreet)
                }
            }
        }
    }

    fun triggerVoiceSuggestion(prompt: String) {
        viewModelScope.launch {
            if (!permissionManager.hasRecordAudio()) {
                _transientBanner.value = MarcoConfig.MISSING_PERMISSION_LINE
                return@launch
            }
            if (!uiState.value.settings.assistantEnabled) {
                settingsRepo.setAssistantEnabled(true)
            }
            if (uiState.value.connectionState == ConnectionState.LIVE) {
                liveSessionManager.sendTextTurn(prompt)
            } else {
                runtime.activateLiveSession(greetUser = false, initialPrompt = prompt)
            }
        }
    }

    fun toggleAssistantEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAssistantEnabled(enabled)
            if (!enabled) {
                runtime.disableAssistantCompletely()
                BackgroundAudioService.stopService(getApplication())
            } else {
                val updated = settingsRepo.settingsFlow.first()
                syncBackgroundAndWakeState(updated)
            }
        }
    }

    fun toggleMute() {
        viewModelScope.launch {
            val current = settingsRepo.settingsFlow.first()
            val next = !current.isMuted
            settingsRepo.setMuted(next)
            audioStreamManager.setMuted(next)
        }
    }

    fun endLiveSessionToStandby() {
        viewModelScope.launch {
            runtime.pauseToStandbyOrOffline()
        }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setWakeWordEnabled(enabled)
            val updated = settingsRepo.settingsFlow.first()
            syncBackgroundAndWakeState(updated)
        }
    }

    fun setWakeSensitivity(value: Float) {
        viewModelScope.launch {
            settingsRepo.setWakeSensitivity(value)
            wakeWordDetector.updateSensitivity(value)
        }
    }

    fun setBackgroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setBackgroundServiceEnabled(enabled)
            val updated = settingsRepo.settingsFlow.first()
            syncBackgroundAndWakeState(updated)
        }
    }

    fun setSelectedVoice(voiceId: String) {
        viewModelScope.launch {
            settingsRepo.setSelectedVoice(voiceId)
            _transientBanner.value = "Voice updated to $voiceId for next session."
        }
    }

    fun setSelectedModel(modelId: String) {
        viewModelScope.launch {
            settingsRepo.setSelectedModel(modelId)
            _transientBanner.value = "Live model updated."
        }
    }

    fun setNotificationReaderConsent(consent: Boolean) {
        viewModelScope.launch {
            settingsRepo.setNotificationReaderConsent(consent)
            NotificationMemoryStore.setConsentEnabled(
                consent && permissionManager.isNotificationAccessGranted()
            )
            refreshSystemStatus()
        }
    }

    fun confirmPendingAction() {
        val pending = conversationManager.pendingConfirmation.value ?: return
        val (ok, msg) = toolExecutionEngine.executeConfirmedAction(pending)
        _transientBanner.value = msg
        if (ok && uiState.value.connectionState == ConnectionState.LIVE) {
            viewModelScope.launch {
                liveSessionManager.sendTextTurn("Action confirmed by Sanni and executed: $msg")
            }
        }
    }

    fun cancelPendingAction() {
        val pending = conversationManager.pendingConfirmation.value ?: return
        toolExecutionEngine.cancelPendingConfirmation()
        _transientBanner.value = "Cancelled ${pending.title}"
        if (uiState.value.connectionState == ConnectionState.LIVE) {
            viewModelScope.launch {
                liveSessionManager.sendTextTurn("Sanni cancelled the pending action.")
            }
        }
    }

    fun dismissBanner() {
        _transientBanner.value = null
    }

    fun buildNotificationAccessIntent(): Intent =
        permissionManager.createNotificationListenerSettingsIntent()

    fun buildBatteryOptimizationIntent(): Intent =
        permissionManager.createBatteryOptimizationSettingsIntent()

    private fun syncBackgroundAndWakeState(settings: MarcoSettings) {
        if (!settings.assistantEnabled) {
            runtime.stopStandbyWakeWord()
            BackgroundAudioService.stopService(getApplication())
            return
        }
        if (!permissionManager.hasRecordAudio()) {
            return
        }
        if (settings.backgroundServiceEnabled) {
            BackgroundAudioService.startService(getApplication(), BackgroundAudioService.ACTION_START_STANDBY)
        } else {
            BackgroundAudioService.stopService(getApplication())
            if (settings.wakeWordEnabled) {
                runtime.startStandbyWakeWord(settings)
            } else {
                runtime.stopStandbyWakeWord()
            }
        }
    }

    private data class ConnectionOrbTuple(
        val conn: ConnectionState,
        val orb: OrbState,
        val standby: Boolean,
        val speaking: Boolean
    )

    private data class AudioLevelsTuple(
        val mic: Float,
        val spk: Float,
        val standbyMic: Float
    )

    private data class AudioStatusSnapshot(
        val conn: ConnectionState,
        val orb: OrbState,
        val standby: Boolean,
        val speaking: Boolean,
        val micLevel: Float,
        val speakerLevel: Float,
        val standbyMic: Float
    )

    private data class CaptionSnapshot(
        val userText: String,
        val marcoText: String,
        val toolName: String?,
        val error: String?,
        val confirmation: PendingConfirmation?,
        val banner: String?
    )
}
