package com.example.domain.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.domain.model.PermissionStatus
import com.example.domain.model.PermissionType

class PermissionManager(private val context: Context) {

    fun isGranted(type: PermissionType): Boolean {
        if (type == PermissionType.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            type.manifestPermission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllPermissionStatuses(): List<PermissionStatus> {
        return PermissionType.entries.map { type ->
            PermissionStatus(type = type, isGranted = isGranted(type))
        }
    }

    fun hasRecordAudio(): Boolean = isGranted(PermissionType.RECORD_AUDIO)

    fun hasReadContacts(): Boolean = isGranted(PermissionType.READ_CONTACTS)

    fun hasCallPhone(): Boolean = isGranted(PermissionType.CALL_PHONE)

    fun hasPostNotifications(): Boolean = isGranted(PermissionType.POST_NOTIFICATIONS)

    fun isNotificationAccessGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
