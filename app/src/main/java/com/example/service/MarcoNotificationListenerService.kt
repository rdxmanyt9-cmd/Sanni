package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.domain.model.NotificationSummary
import java.util.concurrent.CopyOnWriteArrayList

object NotificationMemoryStore {
    @Volatile
    var isConsentEnabled: Boolean = false
        private set

    private val recentNotifications = CopyOnWriteArrayList<NotificationSummary>()

    fun setConsentEnabled(enabled: Boolean) {
        isConsentEnabled = enabled
        if (!enabled) {
            recentNotifications.clear()
        }
    }

    fun record(summary: NotificationSummary) {
        if (!isConsentEnabled) return
        recentNotifications.add(0, summary)
        while (recentNotifications.size > 15) {
            recentNotifications.removeAt(recentNotifications.lastIndex)
        }
    }

    fun getRecent(): List<NotificationSummary> {
        return if (isConsentEnabled) recentNotifications.toList() else emptyList()
    }

    fun clear() {
        recentNotifications.clear()
    }
}

class MarcoNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !NotificationMemoryStore.isConsentEnabled) return
        if (sbn.packageName == packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        NotificationMemoryStore.record(
            NotificationSummary(
                packageName = sbn.packageName,
                appName = appLabel,
                title = title,
                text = text,
                postTime = sbn.postTime
            )
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationMemoryStore.clear()
    }
}
