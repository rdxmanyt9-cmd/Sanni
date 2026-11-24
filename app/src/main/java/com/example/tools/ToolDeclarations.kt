package com.example.tools

import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool

object ToolDeclarations {

    val allFunctions: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "open_application",
            description = "Opens an installed Android application on Sanni's phone, such as YouTube, Instagram, WhatsApp, Chrome, Calculator, Gmail, Spotify, Settings, etc.",
            parameters = mapOf(
                "app_name" to Schema.string("Human-readable name of the application to open, e.g. 'YouTube', 'Instagram', 'WhatsApp', 'Chrome', 'Calculator', 'Gmail'")
            )
        ),
        FunctionDeclaration(
            name = "search_contacts",
            description = "Searches Sanni's Android phone contacts by name or nickname (for example 'Mummy', 'Papa', 'Rahul') and returns matching names and phone numbers.",
            parameters = mapOf(
                "query" to Schema.string("Contact name or partial name to search for")
            )
        ),
        FunctionDeclaration(
            name = "prepare_phone_call",
            description = "Prepares a phone call to a contact or phone number and requests Sanni's explicit confirmation before placing the call. Always call this instead of claiming a call was placed.",
            parameters = mapOf(
                "contact_name" to Schema.string("Display name of the person to call"),
                "phone_number" to Schema.string("Phone number to dial")
            )
        ),
        FunctionDeclaration(
            name = "prepare_whatsapp_message",
            description = "Prepares a WhatsApp message to a contact and requests Sanni's explicit confirmation before opening WhatsApp with the message prefilled.",
            parameters = mapOf(
                "contact_name" to Schema.string("Recipient contact name"),
                "phone_number" to Schema.string("Recipient phone number with country code if available, e.g. +919876543210 or 9876543210"),
                "message_text" to Schema.string("Message text to prefill in WhatsApp")
            )
        ),
        FunctionDeclaration(
            name = "prepare_gmail_email",
            description = "Prepares a Gmail email with recipient, subject, and body, and requests Sanni's explicit confirmation before opening Gmail.",
            parameters = mapOf(
                "recipient_email" to Schema.string("Recipient email address"),
                "subject" to Schema.string("Email subject line"),
                "body" to Schema.string("Email body content")
            )
        ),
        FunctionDeclaration(
            name = "confirm_pending_action",
            description = "Confirms or cancels the currently pending sensitive action (phone call, WhatsApp message, or Gmail email) when Sanni responds verbally.",
            parameters = mapOf(
                "decision" to Schema.enumeration(
                    values = listOf("confirm", "cancel"),
                    description = "Whether Sanni confirmed ('confirm') or cancelled ('cancel') the pending action"
                )
            )
        ),
        FunctionDeclaration(
            name = "get_current_datetime",
            description = "Gets the current local date, day of week, and time for Sanni.",
            parameters = mapOf(
                "timezone_hint" to Schema.string("Optional timezone or format preference; omit to use the device local timezone")
            ),
            optionalParameters = listOf("timezone_hint")
        ),
        FunctionDeclaration(
            name = "set_alarm",
            description = "Sets an alarm on Sanni's phone using the Android AlarmClock.",
            parameters = mapOf(
                "hour" to Schema.integer("Hour in 24-hour format (0 to 23)"),
                "minute" to Schema.integer("Minute (0 to 59)"),
                "label" to Schema.string("Optional label or title for the alarm")
            ),
            optionalParameters = listOf("label")
        ),
        FunctionDeclaration(
            name = "set_timer_or_reminder",
            description = "Sets a countdown timer in minutes or creates a calendar reminder event.",
            parameters = mapOf(
                "title" to Schema.string("Reminder or timer title"),
                "minutes_from_now" to Schema.integer("Number of minutes from now (must be >= 1)"),
                "kind" to Schema.enumeration(
                    values = listOf("timer", "reminder"),
                    description = "Use 'timer' for countdown timers and 'reminder' for calendar reminders"
                )
            )
        ),
        FunctionDeclaration(
            name = "web_search",
            description = "Searches the internet for a query in Sanni's default browser or Google search.",
            parameters = mapOf(
                "query" to Schema.string("Search query string")
            )
        ),
        FunctionDeclaration(
            name = "control_device_setting",
            description = "Controls supported Android device settings or opens specific system setting panels.",
            parameters = mapOf(
                "setting_name" to Schema.enumeration(
                    values = listOf("flashlight", "volume", "wifi", "bluetooth", "display", "dnd", "battery"),
                    description = "Target setting to adjust or open"
                ),
                "action" to Schema.enumeration(
                    values = listOf("on", "off", "up", "down", "mute", "max", "open"),
                    description = "Action to apply: 'on'/'off' for flashlight, 'up'/'down'/'mute'/'max' for volume, 'open' for system panels"
                )
            )
        ),
        FunctionDeclaration(
            name = "read_notifications",
            description = "Reads recent notifications only if Sanni has enabled Android Notification Access and consented in Marco Settings.",
            parameters = mapOf(
                "limit" to Schema.integer("Optional maximum number of recent notifications to return (default 5)")
            ),
            optionalParameters = listOf("limit")
        )
    )

    val toolsList: List<Tool> = listOf(
        Tool.functionDeclarations(allFunctions)
    )
}
