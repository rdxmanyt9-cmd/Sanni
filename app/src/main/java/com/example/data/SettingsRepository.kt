package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.MarcoConfig
import com.example.domain.model.MarcoSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.marcoDataStore: DataStore<Preferences> by preferencesDataStore(name = "marco_prefs")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val ASSISTANT_ENABLED = booleanPreferencesKey("assistant_enabled")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val WAKE_SENSITIVITY = floatPreferencesKey("wake_sensitivity")
        val BACKGROUND_SERVICE_ENABLED = booleanPreferencesKey("background_service_enabled")
        val SELECTED_VOICE = stringPreferencesKey("selected_voice")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val NOTIFICATION_READER_CONSENT = booleanPreferencesKey("notification_reader_consent")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val IS_MUTED = booleanPreferencesKey("is_muted")
    }

    val settingsFlow: Flow<MarcoSettings> = context.marcoDataStore.data.map { prefs ->
        MarcoSettings(
            assistantEnabled = prefs[Keys.ASSISTANT_ENABLED] ?: true,
            wakeWordEnabled = prefs[Keys.WAKE_WORD_ENABLED] ?: true,
            wakeSensitivity = prefs[Keys.WAKE_SENSITIVITY] ?: 0.65f,
            backgroundServiceEnabled = prefs[Keys.BACKGROUND_SERVICE_ENABLED] ?: true,
            selectedVoice = prefs[Keys.SELECTED_VOICE] ?: MarcoConfig.DEFAULT_VOICE,
            selectedModel = prefs[Keys.SELECTED_MODEL] ?: MarcoConfig.DEFAULT_LIVE_MODEL,
            notificationReaderConsent = prefs[Keys.NOTIFICATION_READER_CONSENT] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            isMuted = prefs[Keys.IS_MUTED] ?: false
        )
    }

    suspend fun setAssistantEnabled(enabled: Boolean) {
        context.marcoDataStore.edit { it[Keys.ASSISTANT_ENABLED] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.marcoDataStore.edit { it[Keys.WAKE_WORD_ENABLED] = enabled }
    }

    suspend fun setWakeSensitivity(sensitivity: Float) {
        context.marcoDataStore.edit { it[Keys.WAKE_SENSITIVITY] = sensitivity.coerceIn(0.2f, 1.0f) }
    }

    suspend fun setBackgroundServiceEnabled(enabled: Boolean) {
        context.marcoDataStore.edit { it[Keys.BACKGROUND_SERVICE_ENABLED] = enabled }
    }

    suspend fun setSelectedVoice(voice: String) {
        context.marcoDataStore.edit { it[Keys.SELECTED_VOICE] = voice }
    }

    suspend fun setSelectedModel(model: String) {
        context.marcoDataStore.edit { it[Keys.SELECTED_MODEL] = model }
    }

    suspend fun setNotificationReaderConsent(consent: Boolean) {
        context.marcoDataStore.edit { it[Keys.NOTIFICATION_READER_CONSENT] = consent }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.marcoDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setMuted(muted: Boolean) {
        context.marcoDataStore.edit { it[Keys.IS_MUTED] = muted }
    }
}
