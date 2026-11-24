package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.LiveSessionManager
import com.example.audio.AudioStreamManager
import com.example.audio.WakeWordDetector
import com.example.data.SettingsRepository
import com.example.domain.manager.ConversationManager
import com.example.domain.manager.PermissionManager
import com.example.domain.model.ConnectionState
import com.example.domain.model.MarcoSettings
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shared runtime container ensuring UI and BackgroundAudioService coordinate a single
 * audio stream, wake-word detector, and Gemini Live session without mic contention.
 */
object MarcoRuntime {
    @Volatile
    private var instance: Container? = null

    class Container(context: Context) {
        val appContext: Context = context.applicationContext
        val settingsRepository = SettingsRepository(appContext)
        val permissionManager = PermissionManager(appContext)
        val conversationManager = ConversationManager()
        val audioStreamManager = AudioStreamManager()
        val wakeWordDetector = WakeWordDetector(appContext)
        val toolExecutionEngine = ToolExecutionEngine(
            context = appContext,
            permissionManager = permissionManager,
            conversationManager = conversationManager
        )
        val liveSessionManager = LiveSessionManager(
            audioStreamManager = audioStreamManager,
            toolExecutionEngine = toolExecutionEngine,
            conversationManager = conversationManager,
            permissionManager = permissionManager
        )

        private val coordinatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @Volatile
        var lastKnownMuted: Boolean = false

        private val _isStandbyActive = MutableStateFlow(false)
        val isStandbyActive: StateFlow<Boolean> = _isStandbyActive.asStateFlow()

        fun startStandbyWakeWord(settings: MarcoSettings) {
            lastKnownMuted = settings.isMuted
            if (!settings.assistantEnabled || !settings.wakeWordEnabled) {
                stopStandbyWakeWord()
                return
            }
            if (!permissionManager.hasRecordAudio()) return
            if (liveSessionManager.connectionState.value == ConnectionState.LIVE ||
                liveSessionManager.connectionState.value == ConnectionState.CONNECTING
            ) {
                return
            }
            wakeWordDetector.updateSensitivity(settings.wakeSensitivity)
            _isStandbyActive.value = true
            wakeWordDetector.start {
                coordinatorScope.launch {
                    _isStandbyActive.value = false
                    val latest = settingsRepository.settingsFlow.first()
                    lastKnownMuted = latest.isMuted
                    liveSessionManager.startLiveSession(
                        settings = latest,
                        greetUser = false
                    )
                }
            }
        }

        fun stopStandbyWakeWord() {
            wakeWordDetector.stop()
            _isStandbyActive.value = false
        }

        suspend fun activateLiveSession(
            greetUser: Boolean = false,
            initialPrompt: String? = null
        ) {
            stopStandbyWakeWord()
            val settings = settingsRepository.settingsFlow.first()
            lastKnownMuted = settings.isMuted
            liveSessionManager.startLiveSession(
                settings = settings,
                greetUser = greetUser,
                initialPrompt = initialPrompt
            )
        }

        suspend fun pauseToStandbyOrOffline() {
            liveSessionManager.stopLiveSession()
            val settings = settingsRepository.settingsFlow.first()
            lastKnownMuted = settings.isMuted
            if (settings.assistantEnabled && settings.wakeWordEnabled && permissionManager.hasRecordAudio()) {
                startStandbyWakeWord(settings)
            }
        }

        suspend fun disableAssistantCompletely() {
            stopStandbyWakeWord()
            liveSessionManager.stopLiveSession()
            audioStreamManager.releaseAll()
        }
    }

    fun get(context: Context): Container {
        return instance ?: synchronized(this) {
            instance ?: Container(context).also { instance = it }
        }
    }
}

class BackgroundAudioService : Service() {

    companion object {
        private const val TAG = "BackgroundAudioService"
        private const val CHANNEL_ID = "marco_voice_channel"
        private const val NOTIFICATION_ID = 1007

        const val ACTION_START_STANDBY = "com.example.action.START_STANDBY"
        const val ACTION_WAKE_MARCO = "com.example.action.WAKE_MARCO"
        const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
        const val ACTION_STOP_ASSISTANT = "com.example.action.STOP_ASSISTANT"

        fun startService(context: Context, action: String = ACTION_START_STANDBY) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, BackgroundAudioService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: MarcoRuntime.Container

    override fun onCreate() {
        super.onCreate()
        runtime = MarcoRuntime.get(this)
        createNotificationChannel()
        observeStateForNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!runtime.permissionManager.hasRecordAudio()) {
            Log.w(TAG, "RECORD_AUDIO missing; cannot run microphone foreground service")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(
            connectionState = runtime.liveSessionManager.connectionState.value,
            isStandby = runtime.isStandbyActive.value,
            isMuted = runtime.lastKnownMuted
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        when (intent?.action) {
            ACTION_WAKE_MARCO -> {
                serviceScope.launch {
                    runtime.activateLiveSession(greetUser = false)
                }
            }
            ACTION_TOGGLE_MUTE -> {
                serviceScope.launch {
                    val current = runtime.settingsRepository.settingsFlow.first()
                    val nextMuted = !current.isMuted
                    runtime.lastKnownMuted = nextMuted
                    runtime.settingsRepository.setMuted(nextMuted)
                    runtime.audioStreamManager.setMuted(nextMuted)
                }
            }
            ACTION_STOP_ASSISTANT -> {
                serviceScope.launch {
                    runtime.settingsRepository.setAssistantEnabled(false)
                    runtime.disableAssistantCompletely()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            else -> {
                serviceScope.launch {
                    val settings = runtime.settingsRepository.settingsFlow.first()
                    runtime.lastKnownMuted = settings.isMuted
                    if (settings.assistantEnabled && settings.wakeWordEnabled) {
                        runtime.startStandbyWakeWord(settings)
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        runtime.stopStandbyWakeWord()
        Log.d(TAG, "BackgroundAudioService destroyed")
    }

    private fun observeStateForNotification() {
        serviceScope.launch {
            runtime.liveSessionManager.connectionState.collect { state ->
                val settings = runtime.settingsRepository.settingsFlow.first()
                runtime.lastKnownMuted = settings.isMuted
                updateNotification(state, runtime.isStandbyActive.value, settings.isMuted)
            }
        }
        serviceScope.launch {
            runtime.isStandbyActive.collect { standby ->
                val settings = runtime.settingsRepository.settingsFlow.first()
                runtime.lastKnownMuted = settings.isMuted
                updateNotification(runtime.liveSessionManager.connectionState.value, standby, settings.isMuted)
            }
        }
    }

    private fun updateNotification(
        connectionState: ConnectionState,
        isStandby: Boolean,
        isMuted: Boolean
    ) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(connectionState, isStandby, isMuted))
    }

    private fun buildNotification(
        connectionState: ConnectionState,
        isStandby: Boolean,
        isMuted: Boolean
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val wakeIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BackgroundAudioService::class.java).setAction(ACTION_WAKE_MARCO),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, BackgroundAudioService::class.java).setAction(ACTION_STOP_ASSISTANT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusTitle = when {
            connectionState == ConnectionState.LIVE && isMuted -> "Marco Live (Mic Muted)"
            connectionState == ConnectionState.LIVE -> "Marco Live • Listening"
            connectionState == ConnectionState.CONNECTING -> "Marco Connecting..."
            isStandby -> "Marco Standby • Say \"Hey Marco\""
            else -> "Marco Ready"
        }

        val statusText = when {
            connectionState == ConnectionState.LIVE -> "Real-time Gemini Live voice active for Sanni"
            isStandby -> "Microphone listening locally for Hey Marco"
            else -> "Tap Wake Marco to start voice conversation"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(statusTitle)
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, "Wake Marco", wakeIntent)
            .addAction(0, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fg_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
