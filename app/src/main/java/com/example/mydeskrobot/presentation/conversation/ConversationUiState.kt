package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.model.RobotEmotion

data class ConversationUiState(
    val phase: ConversationPhase = ConversationPhase.Idle,
    val emotion: RobotEmotion = RobotEmotion.NEUTRAL,
    val statusMessage: String = "",
    /** Dialogo: righe Tu / Robot. */
    val conversationLog: String = "",
    /** Frase in costruzione (prima dei 5s di pausa). */
    val currentUtterance: String = "",
    val wakePhraseHint: String = "",
    val exitPhraseHint: String = "",
    val isHotwordListeningActive: Boolean = false,
    /** True tra mezzanotte e l'ora di fine notte configurata (standby dormiente). */
    val isNightMode: Boolean = false,
    /** True while background LLM scans conversation log for durable user memories. */
    val isMemoryExtracting: Boolean = false,
    /** User poked left eye — force closed until cleared. */
    val eyeSquishLeft: Boolean = false,
    /** User poked right eye — force closed until cleared. */
    val eyeSquishRight: Boolean = false,
) {
    val displayText: String
        get() = buildString {
            if (conversationLog.isNotBlank()) {
                append(conversationLog.trimEnd())
            }
            if (currentUtterance.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("… ")
                append(currentUtterance)
            }
        }
}

sealed interface ConversationPhase {
    data object Idle : ConversationPhase
    data object WaitingForHotword : ConversationPhase
    data object ActiveListening : ConversationPhase
    data object Thinking : ConversationPhase
    /** Scatto fotocamera per analisi visione LLM. */
    data object CapturingImage : ConversationPhase
    data object Speaking : ConversationPhase
    data class Error(val message: String) : ConversationPhase
}
