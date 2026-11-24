package com.example

import com.example.core.MarcoConfig
import com.example.domain.manager.ConversationManager
import com.example.domain.model.NotificationSummary
import com.example.domain.model.PendingConfirmation
import com.example.service.NotificationMemoryStore
import com.example.tools.ToolDeclarations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `marco config preserves personal identity and developer branding`() {
        assertEquals("Marco", MarcoConfig.ASSISTANT_NAME)
        assertEquals("Male", MarcoConfig.ASSISTANT_GENDER)
        assertEquals("Sanni Kumar", MarcoConfig.PRIMARY_USER_NAME)
        assertEquals("Sanni Kumar", MarcoConfig.DEVELOPER_NAME)
        assertEquals("1.0", MarcoConfig.APP_VERSION)
        assertTrue(MarcoConfig.INTRO_GREETING.contains("Hey Sanni! Main Marco hoon"))
        assertTrue(MarcoConfig.MALE_VOICES.any { it.id == "Fenrir" })
    }

    @Test
    fun `tool declarations include all required device control capabilities`() {
        assertEquals(12, ToolDeclarations.allFunctions.size)
        assertEquals(1, ToolDeclarations.toolsList.size)
        val paramsField = com.google.firebase.ai.type.FunctionDeclaration::class.java
            .getDeclaredField("parameters")
            .apply { isAccessible = true }
        assertTrue(
            "Every FunctionDeclaration must have non-empty parameters for Gemini Live OpenAPI schema validation",
            ToolDeclarations.allFunctions.all { declaration ->
                val params = paramsField.get(declaration) as? Map<*, *>
                params != null && params.isNotEmpty()
            }
        )
    }

    @Test
    fun `conversation manager tracks turns and pending confirmation safely`() {
        val manager = ConversationManager()
        manager.appendUserTranscriptDelta("Marco, kya kar raha hai?")
        manager.appendMarcoTranscriptDelta("Bas tera intezar bhai!")
        manager.commitTurnIfPresent()

        val summary = manager.buildRecentContextSummary()
        assertTrue(summary.contains("Sanni: Marco, kya kar raha hai?"))
        assertTrue(summary.contains("Marco: Bas tera intezar bhai!"))

        val confirmation = PendingConfirmation.WhatsAppMessage(
            id = "1",
            contactName = "Rahul",
            phoneNumber = "+919876543210",
            messageBody = "Bhai kal milte hain"
        )
        manager.setPendingConfirmation(confirmation)
        assertNotNull(manager.pendingConfirmation.value)

        manager.setPendingConfirmation(null)
        assertNull(manager.pendingConfirmation.value)
    }

    @Test
    fun `notification memory store strictly enforces consent`() {
        NotificationMemoryStore.setConsentEnabled(false)
        NotificationMemoryStore.record(
            NotificationSummary(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Mummy",
                text = "Khana kha liya?",
                postTime = 1000L
            )
        )
        assertTrue(NotificationMemoryStore.getRecent().isEmpty())

        NotificationMemoryStore.setConsentEnabled(true)
        NotificationMemoryStore.record(
            NotificationSummary(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                title = "Mummy",
                text = "Khana kha liya?",
                postTime = 1000L
            )
        )
        assertEquals(1, NotificationMemoryStore.getRecent().size)

        NotificationMemoryStore.setConsentEnabled(false)
        assertFalse(NotificationMemoryStore.isConsentEnabled)
        assertTrue(NotificationMemoryStore.getRecent().isEmpty())
    }
}
