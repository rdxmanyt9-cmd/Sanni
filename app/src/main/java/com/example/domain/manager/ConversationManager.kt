package com.example.domain.manager

import com.example.domain.model.ConversationTurn
import com.example.domain.model.PendingConfirmation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class ConversationManager {

    private val lock = ReentrantLock()
    private val maxTurns = 30

    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val turns: StateFlow<List<ConversationTurn>> = _turns.asStateFlow()

    private val _liveUserTranscript = MutableStateFlow("")
    val liveUserTranscript: StateFlow<String> = _liveUserTranscript.asStateFlow()

    private val _liveMarcoTranscript = MutableStateFlow("")
    val liveMarcoTranscript: StateFlow<String> = _liveMarcoTranscript.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    fun appendUserTranscriptDelta(delta: String) {
        if (delta.isEmpty()) return
        lock.withLock {
            _liveUserTranscript.update { (it + delta).takeLast(240) }
        }
    }

    fun appendMarcoTranscriptDelta(delta: String) {
        if (delta.isEmpty()) return
        lock.withLock {
            _liveMarcoTranscript.update { (it + delta).takeLast(240) }
        }
    }

    fun commitTurnIfPresent() {
        lock.withLock {
            val userText = _liveUserTranscript.value.trim()
            val marcoText = _liveMarcoTranscript.value.trim()
            if (userText.isNotEmpty()) {
                appendTurnLocked("Sanni", userText)
                _liveUserTranscript.value = ""
            }
            if (marcoText.isNotEmpty()) {
                appendTurnLocked("Marco", marcoText)
                _liveMarcoTranscript.value = ""
            }
        }
    }

    fun addSystemNote(note: String) {
        lock.withLock {
            appendTurnLocked("System", note)
        }
    }

    fun clearLiveTranscripts() {
        lock.withLock {
            _liveUserTranscript.value = ""
            _liveMarcoTranscript.value = ""
        }
    }

    fun setPendingConfirmation(confirmation: PendingConfirmation?) {
        _pendingConfirmation.value = confirmation
    }

    fun buildRecentContextSummary(): String {
        val snapshot = _turns.value.takeLast(8)
        if (snapshot.isEmpty()) return ""
        return snapshot.joinToString("\n") { "${it.speaker}: ${it.text}" }
    }

    fun clearSessionHistory() {
        lock.withLock {
            _turns.value = emptyList()
            _liveUserTranscript.value = ""
            _liveMarcoTranscript.value = ""
            _pendingConfirmation.value = null
        }
    }

    private fun appendTurnLocked(speaker: String, text: String) {
        val updated = (_turns.value + ConversationTurn(speaker = speaker, text = text))
            .takeLast(maxTurns)
        _turns.value = updated
    }
}
