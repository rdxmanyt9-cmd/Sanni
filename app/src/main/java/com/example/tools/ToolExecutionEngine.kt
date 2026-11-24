package com.example.tools

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import com.example.core.MarcoConfig
import com.example.domain.manager.ConversationManager
import com.example.domain.manager.PermissionManager
import com.example.domain.model.PendingConfirmation
import com.example.service.NotificationMemoryStore
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionResponsePart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class ToolExecutionEngine(
    private val context: Context,
    private val permissionManager: PermissionManager,
    private val conversationManager: ConversationManager
) {
    companion object {
        private const val TAG = "ToolExecutionEngine"

        private val KNOWN_APP_PACKAGES = mapOf(
            "youtube" to listOf("com.google.android.youtube"),
            "instagram" to listOf("com.instagram.android"),
            "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
            "chrome" to listOf("com.android.chrome"),
            "google chrome" to listOf("com.android.chrome"),
            "gmail" to listOf("com.google.android.gm"),
            "calculator" to listOf(
                "com.google.android.calculator",
                "com.android.calculator2",
                "com.sec.android.app.popupcalculator"
            ),
            "camera" to listOf("com.android.camera", "com.google.android.GoogleCamera"),
            "settings" to listOf("com.android.settings"),
            "maps" to listOf("com.google.android.apps.maps"),
            "spotify" to listOf("com.spotify.music"),
            "clock" to listOf("com.google.android.deskclock", "com.android.deskclock")
        )
    }

    suspend fun execute(call: FunctionCallPart): FunctionResponsePart = withContext(Dispatchers.IO) {
        val resultJson = try {
            when (call.name) {
                "open_application" -> handleOpenApplication(call)
                "search_contacts" -> handleSearchContacts(call)
                "prepare_phone_call" -> handlePreparePhoneCall(call)
                "prepare_whatsapp_message" -> handlePrepareWhatsApp(call)
                "prepare_gmail_email" -> handlePrepareGmail(call)
                "confirm_pending_action" -> handleVoiceConfirmation(call)
                "get_current_datetime" -> handleGetCurrentDateTime()
                "set_alarm" -> handleSetAlarm(call)
                "set_timer_or_reminder" -> handleSetTimerOrReminder(call)
                "web_search" -> handleWebSearch(call)
                "control_device_setting" -> handleControlDeviceSetting(call)
                "read_notifications" -> handleReadNotifications(call)
                else -> errorJson("Unknown function: ${call.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution error for ${call.name}", e)
            errorJson(e.localizedMessage ?: "Tool execution failed")
        }

        FunctionResponsePart(
            name = call.name,
            response = resultJson,
            id = call.id
        )
    }

    fun executeConfirmedAction(confirmation: PendingConfirmation): Pair<Boolean, String> {
        conversationManager.setPendingConfirmation(null)
        return when (confirmation) {
            is PendingConfirmation.PhoneCall -> launchPhoneCall(confirmation.contactName, confirmation.phoneNumber)
            is PendingConfirmation.WhatsAppMessage -> launchWhatsApp(
                confirmation.contactName,
                confirmation.phoneNumber,
                confirmation.messageBody
            )
            is PendingConfirmation.GmailEmail -> launchGmail(
                confirmation.recipientEmail,
                confirmation.subject,
                confirmation.body
            )
        }
    }

    fun cancelPendingConfirmation() {
        conversationManager.setPendingConfirmation(null)
    }

    private fun handleOpenApplication(call: FunctionCallPart): JsonObject {
        val appName = call.argString("app_name").orEmpty().trim()
        if (appName.isEmpty()) {
            return errorJson("Missing app_name parameter")
        }
        val normalized = appName.lowercase(Locale.getDefault())
        val pm = context.packageManager

        // 1. Direct known package match
        KNOWN_APP_PACKAGES[normalized]?.forEach { pkg ->
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return successJson("Opened $appName")
            }
        }

        // 2. Fuzzy match across installed launcher apps via Play-compliant <queries>
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(mainIntent, 0)
        }

        val bestMatch = installed.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(Locale.getDefault())
            label == normalized || label.contains(normalized) || normalized.contains(label)
        }

        if (bestMatch != null) {
            val pkg = bestMatch.activityInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val label = bestMatch.loadLabel(pm).toString()
                return successJson("Opened $label")
            }
        }

        return errorJson("Could not find $appName installed on Sanni's phone.")
    }

    private fun handleSearchContacts(call: FunctionCallPart): JsonObject {
        if (!permissionManager.hasReadContacts()) {
            return permissionMissingJson("READ_CONTACTS")
        }
        val query = call.argString("query").orEmpty().trim()
        if (query.isEmpty()) {
            return errorJson("Contact search query is empty.")
        }

        val matches = mutableListOf<JsonObject>()
        val seen = mutableSetOf<String>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && matches.size < 6) {
                val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                val dedupeKey = "$name|$number"
                if (name.isNotBlank() && number.isNotBlank() && seen.add(dedupeKey)) {
                    matches.add(
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(name),
                                "phone_number" to JsonPrimitive(number)
                            )
                        )
                    )
                }
            }
        }

        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "count" to JsonPrimitive(matches.size),
                "contacts" to JsonArray(matches),
                "message" to JsonPrimitive(
                    if (matches.isEmpty()) "No contacts found matching '$query'."
                    else "Found ${matches.size} contact(s) matching '$query'."
                )
            )
        )
    }

    private fun handlePreparePhoneCall(call: FunctionCallPart): JsonObject {
        if (!permissionManager.hasCallPhone()) {
            return permissionMissingJson("CALL_PHONE")
        }
        val contactName = call.argString("contact_name").orEmpty().ifBlank { "Unknown Contact" }
        val phoneNumber = call.argString("phone_number").orEmpty().trim()
        if (phoneNumber.isEmpty()) {
            return errorJson("Phone number is required to prepare a call.")
        }

        val confirmation = PendingConfirmation.PhoneCall(
            id = UUID.randomUUID().toString(),
            contactName = contactName,
            phoneNumber = phoneNumber
        )
        conversationManager.setPendingConfirmation(confirmation)

        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "status" to JsonPrimitive("awaiting_confirmation"),
                "message" to JsonPrimitive("Call to $contactName ($phoneNumber) is prepared. Ask Sanni to confirm before calling.")
            )
        )
    }

    private fun handlePrepareWhatsApp(call: FunctionCallPart): JsonObject {
        val contactName = call.argString("contact_name").orEmpty().ifBlank { "Contact" }
        val phoneNumber = call.argString("phone_number").orEmpty().trim()
        val messageText = call.argString("message_text").orEmpty().trim()
        if (messageText.isEmpty()) {
            return errorJson("Message text is empty.")
        }

        val confirmation = PendingConfirmation.WhatsAppMessage(
            id = UUID.randomUUID().toString(),
            contactName = contactName,
            phoneNumber = phoneNumber,
            messageBody = messageText
        )
        conversationManager.setPendingConfirmation(confirmation)

        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "status" to JsonPrimitive("awaiting_confirmation"),
                "message" to JsonPrimitive(
                    "WhatsApp message to $contactName is prepared: \"$messageText\". Ask Sanni for explicit confirmation before opening WhatsApp."
                )
            )
        )
    }

    private fun handlePrepareGmail(call: FunctionCallPart): JsonObject {
        val recipient = call.argString("recipient_email").orEmpty().trim()
        val subject = call.argString("subject").orEmpty().trim()
        val body = call.argString("body").orEmpty().trim()
        if (recipient.isEmpty()) {
            return errorJson("Recipient email address is required.")
        }

        val confirmation = PendingConfirmation.GmailEmail(
            id = UUID.randomUUID().toString(),
            recipientEmail = recipient,
            subject = subject,
            body = body
        )
        conversationManager.setPendingConfirmation(confirmation)

        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "status" to JsonPrimitive("awaiting_confirmation"),
                "message" to JsonPrimitive(
                    "Gmail email to $recipient with subject '$subject' is prepared. Ask Sanni to confirm before opening Gmail."
                )
            )
        )
    }

    private fun handleVoiceConfirmation(call: FunctionCallPart): JsonObject {
        val pending = conversationManager.pendingConfirmation.value
            ?: return errorJson("There is no pending action waiting for confirmation.")

        val decision = call.argString("decision").orEmpty().lowercase(Locale.getDefault())
        return if (decision == "confirm") {
            val (ok, msg) = executeConfirmedAction(pending)
            JsonObject(
                mapOf(
                    "success" to JsonPrimitive(ok),
                    "message" to JsonPrimitive(msg)
                )
            )
        } else {
            cancelPendingConfirmation()
            successJson("Cancelled ${pending.title}.")
        }
    }

    private fun handleGetCurrentDateTime(): JsonObject {
        val now = Date()
        val formatted = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(now)
        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "datetime" to JsonPrimitive(formatted),
                "message" to JsonPrimitive("Current date and time is $formatted")
            )
        )
    }

    private fun handleSetAlarm(call: FunctionCallPart): JsonObject {
        val hour = call.argInt("hour") ?: return errorJson("Invalid hour")
        val minute = call.argInt("minute") ?: return errorJson("Invalid minute")
        val label = call.argString("label").orEmpty().ifBlank { "Marco Alarm" }

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            successJson("Alarm set for %02d:%02d (%s)".format(hour, minute, label))
        } catch (e: Exception) {
            errorJson("No clock app available to set alarm: ${e.localizedMessage}")
        }
    }

    private fun handleSetTimerOrReminder(call: FunctionCallPart): JsonObject {
        val title = call.argString("title").orEmpty().ifBlank { "Marco Reminder" }
        val minutes = (call.argInt("minutes_from_now") ?: 5).coerceAtLeast(1)
        val kind = call.argString("kind").orEmpty().lowercase(Locale.getDefault())

        return if (kind == "timer") {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                putExtra(AlarmClock.EXTRA_MESSAGE, title)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                successJson("Timer '$title' started for $minutes minute(s).")
            } catch (e: Exception) {
                errorJson("Unable to start timer: ${e.localizedMessage}")
            }
        } else {
            val startCal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startCal.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startCal.timeInMillis + 15 * 60 * 1000L)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
                successJson("Opened calendar reminder '$title' in $minutes minute(s).")
            } catch (e: Exception) {
                errorJson("Unable to open calendar reminder: ${e.localizedMessage}")
            }
        }
    }

    private fun handleWebSearch(call: FunctionCallPart): JsonObject {
        val query = call.argString("query").orEmpty().trim()
        if (query.isEmpty()) return errorJson("Empty search query")

        return try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            successJson("Searching the web for '$query'")
        } catch (_: Exception) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=$encoded")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            successJson("Opened browser search for '$query'")
        }
    }

    private fun handleControlDeviceSetting(call: FunctionCallPart): JsonObject {
        val setting = call.argString("setting_name").orEmpty().lowercase(Locale.getDefault())
        val action = call.argString("action").orEmpty().lowercase(Locale.getDefault())

        return when (setting) {
            "flashlight" -> toggleFlashlight(action == "on")
            "volume" -> adjustVolume(action)
            "wifi" -> openPanelOrSettings(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_WIFI
                else Settings.ACTION_WIFI_SETTINGS,
                "Wi-Fi settings"
            )
            "bluetooth" -> openPanelOrSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")
            "display" -> openPanelOrSettings(Settings.ACTION_DISPLAY_SETTINGS, "Display & brightness settings")
            "dnd" -> openPanelOrSettings(Settings.ACTION_SOUND_SETTINGS, "Sound & Do Not Disturb settings")
            "battery" -> openPanelOrSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery settings")
            else -> errorJson("Unsupported device setting: $setting")
        }
    }

    private fun handleReadNotifications(call: FunctionCallPart): JsonObject {
        if (!permissionManager.isNotificationAccessGranted()) {
            return JsonObject(
                mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("NOTIFICATION_ACCESS_MISSING"),
                    "message" to JsonPrimitive(
                        "${MarcoConfig.MISSING_PERMISSION_LINE} Notification Access needs to be enabled in Marco Settings."
                    )
                )
            )
        }
        if (!NotificationMemoryStore.isConsentEnabled) {
            return JsonObject(
                mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("NOTIFICATION_CONSENT_OFF"),
                    "message" to JsonPrimitive(
                        "Sanni has not toggled on Notification Reader consent in Marco Settings yet."
                    )
                )
            )
        }

        val limit = (call.argInt("limit") ?: 5).coerceIn(1, 20)
        val items = NotificationMemoryStore.getRecent().take(limit)
        val array = JsonArray(
            items.map { item ->
                JsonObject(
                    mapOf(
                        "app" to JsonPrimitive(item.appName),
                        "title" to JsonPrimitive(item.title),
                        "text" to JsonPrimitive(item.text)
                    )
                )
            }
        )
        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "count" to JsonPrimitive(items.size),
                "notifications" to array,
                "message" to JsonPrimitive(
                    if (items.isEmpty()) "No recent unread notifications captured yet."
                    else "Retrieved ${items.size} recent notifications."
                )
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun launchPhoneCall(contactName: String, phoneNumber: String): Pair<Boolean, String> {
        val cleanNumber = phoneNumber.replace(Regex("[^+0-9*#]"), "")
        val uri = Uri.parse("tel:$cleanNumber")
        return try {
            val action = if (permissionManager.hasCallPhone()) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true to "Calling $contactName ($cleanNumber)"
        } catch (e: Exception) {
            false to "Failed to place call: ${e.localizedMessage}"
        }
    }

    private fun launchWhatsApp(contactName: String, phoneNumber: String, message: String): Pair<Boolean, String> {
        val encoded = URLEncoder.encode(message, "UTF-8")
        val digits = phoneNumber.replace(Regex("[^0-9]"), "")
        val intent = if (digits.isNotEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=$encoded")).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return try {
            context.startActivity(intent)
            true to "Opened WhatsApp with prefilled message for $contactName"
        } catch (_: Exception) {
            try {
                // Fallback without forcing package name
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true to "Opened WhatsApp link for $contactName"
            } catch (e: Exception) {
                false to "WhatsApp is not installed or unavailable: ${e.localizedMessage}"
            }
        }
    }

    private fun launchGmail(recipient: String, subject: String, body: String): Pair<Boolean, String> {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            setPackage("com.google.android.gm")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true to "Opened Gmail compose screen for $recipient"
        } catch (_: Exception) {
            try {
                intent.setPackage(null)
                context.startActivity(intent)
                true to "Opened email app for $recipient"
            } catch (e: Exception) {
                false to "No email client available: ${e.localizedMessage}"
            }
        }
    }

    private fun toggleFlashlight(turnOn: Boolean): JsonObject {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return errorJson("No camera flash found on this device.")

            cameraManager.setTorchMode(cameraId, turnOn)
            successJson("Flashlight turned ${if (turnOn) "on" else "off"}")
        } catch (e: Exception) {
            errorJson("Unable to toggle flashlight: ${e.localizedMessage}")
        }
    }

    private fun adjustVolume(action: String): JsonObject {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (action) {
            "up" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            "down" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            "mute" -> audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            "max" -> {
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, AudioManager.FLAG_SHOW_UI)
            }
            else -> return openPanelOrSettings(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Settings.Panel.ACTION_VOLUME
                else Settings.ACTION_SOUND_SETTINGS,
                "Volume panel"
            )
        }
        return successJson("Media volume adjusted ($action)")
    }

    private fun openPanelOrSettings(action: String, label: String): JsonObject {
        return try {
            val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            successJson("Opened $label")
        } catch (e: Exception) {
            errorJson("Could not open $label: ${e.localizedMessage}")
        }
    }

    private fun FunctionCallPart.argString(key: String): String? =
        args[key]?.jsonPrimitive?.contentOrNull

    private fun FunctionCallPart.argInt(key: String): Int? =
        args[key]?.jsonPrimitive?.intOrNull

    private fun successJson(message: String) = JsonObject(
        mapOf(
            "success" to JsonPrimitive(true),
            "message" to JsonPrimitive(message)
        )
    )

    private fun errorJson(message: String) = JsonObject(
        mapOf(
            "success" to JsonPrimitive(false),
            "error" to JsonPrimitive(message),
            "message" to JsonPrimitive(message)
        )
    )

    private fun permissionMissingJson(permissionName: String) = JsonObject(
        mapOf(
            "success" to JsonPrimitive(false),
            "error" to JsonPrimitive("PERMISSION_DENIED_$permissionName"),
            "message" to JsonPrimitive(
                "${MarcoConfig.MISSING_PERMISSION_LINE} Missing permission: $permissionName"
            )
        )
    )
}
