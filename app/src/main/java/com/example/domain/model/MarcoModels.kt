package com.example.domain.model

enum class OrbState {
    IDLE,
    STANDBY,
    LISTENING,
    THINKING,
    SPEAKING
}

enum class ConnectionState {
    OFFLINE,
    STANDBY_WAKEWORD,
    CONNECTING,
    LIVE,
    RECOVERING,
    ERROR
}

enum class PermissionType(
    val manifestPermission: String,
    val title: String,
    val subtitle: String,
    val marcoPitch: String
) {
    RECORD_AUDIO(
        manifestPermission = android.Manifest.permission.RECORD_AUDIO,
        title = "Microphone Access",
        subtitle = "Required for 'Hey Marco' wake-word and real-time voice conversations.",
        marcoPitch = "Meri ears on kar de bro, warna teri aawaz kaise sununga?"
    ),
    READ_CONTACTS(
        manifestPermission = android.Manifest.permission.READ_CONTACTS,
        title = "Contacts Access",
        subtitle = "Lets Marco find Mummy, friends, and contacts for calls or WhatsApp.",
        marcoPitch = "Contacts access dega tabhi toh Mummy ya doston ka number dhoondh paunga!"
    ),
    CALL_PHONE(
        manifestPermission = android.Manifest.permission.CALL_PHONE,
        title = "Phone Calling",
        subtitle = "Allows Marco to place calls directly after your confirmation.",
        marcoPitch = "Call lagane ke liye permission chahiye — aur haan, bina puche call nahi lagaunga!"
    ),
    POST_NOTIFICATIONS(
        manifestPermission = "android.permission.POST_NOTIFICATIONS",
        title = "Background Status",
        subtitle = "Keeps Marco's persistent status notification visible while listening.",
        marcoPitch = "Notification allow kar de taaki background mein bhi active reh sakoon!"
    )
}

data class PermissionStatus(
    val type: PermissionType,
    val isGranted: Boolean
)

sealed interface PendingConfirmation {
    val id: String
    val title: String
    val summary: String

    data class PhoneCall(
        override val id: String,
        val contactName: String,
        val phoneNumber: String
    ) : PendingConfirmation {
        override val title: String = "Confirm Phone Call"
        override val summary: String = "Call $contactName ($phoneNumber)"
    }

    data class WhatsAppMessage(
        override val id: String,
        val contactName: String,
        val phoneNumber: String,
        val messageBody: String
    ) : PendingConfirmation {
        override val title: String = "Confirm WhatsApp Message"
        override val summary: String = "To $contactName ($phoneNumber): \"$messageBody\""
    }

    data class GmailEmail(
        override val id: String,
        val recipientEmail: String,
        val subject: String,
        val body: String
    ) : PendingConfirmation {
        override val title: String = "Confirm Gmail Draft"
        override val summary: String = "To: $recipientEmail\nSubject: $subject\n\n$body"
    }
}

data class ConversationTurn(
    val speaker: String,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class NotificationSummary(
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val postTime: Long
)

data class MarcoSettings(
    val assistantEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = true,
    val wakeSensitivity: Float = 0.65f,
    val backgroundServiceEnabled: Boolean = true,
    val selectedVoice: String = "Fenrir",
    val selectedModel: String = "gemini-2.5-flash-native-audio-preview-12-2025",
    val notificationReaderConsent: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val isMuted: Boolean = false
)

data class MarcoUiState(
    val orbState: OrbState = OrbState.IDLE,
    val connectionState: ConnectionState = ConnectionState.OFFLINE,
    val micLevel: Float = 0f,
    val speakerLevel: Float = 0f,
    val isMicActive: Boolean = false,
    val isWakeWordListening: Boolean = false,
    val liveInputCaption: String = "",
    val liveMarcoCaption: String = "",
    val statusBanner: String? = null,
    val errorMessage: String? = null,
    val activeToolName: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val permissions: List<PermissionStatus> = emptyList(),
    val isNotificationAccessGranted: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val settings: MarcoSettings = MarcoSettings(),
    val isInitialized: Boolean = false
)
