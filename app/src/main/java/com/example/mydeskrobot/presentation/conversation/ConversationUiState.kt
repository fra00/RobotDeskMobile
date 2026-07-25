package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.check.FireAndCheckEntry
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.IdleDistractionKind
import com.example.mydeskrobot.reasoning.model.RobotProfile

data class ConversationUiState(
    val phase: ConversationPhase = ConversationPhase.Idle,
    val emotion: RobotEmotion = RobotEmotion.NEUTRAL,
    /** Mood intensity 0–1; drives eye expression exaggeration. */
    val emotionIntensity: Float = 0.5f,
    val statusMessage: String = "",
    /** Dialogo: righe Tu / Robot. */
    val conversationLog: String = "",
    /** Debug trace: LLM think, tools, fire-and-check vs fire-and-forget. */
    val reasoningLogText: String = "",
    /** Persistent wellbeing + ephemeral expression snapshot for debug dialog. */
    val moodUiState: MoodUiState = MoodUiState(),
    /** Frase in costruzione (prima dei 5s di pausa). */
    val currentUtterance: String = "",
    val wakePhraseHint: String = "",
    val exitPhraseHint: String = "",
    val isHotwordListeningActive: Boolean = false,
    /** Active robot context profile (WORK, CALL, …); NORMAL when no special context. */
    val robotContextProfile: RobotProfile = RobotProfile.NORMAL,
    /** Pending fire-and-check verification loops. */
    val pendingFireAndChecks: List<FireAndCheckEntry> = emptyList(),
    /** Pending reminders and deferred notifications. */
    val pendingInboxItems: List<PendingInboxItemUi> = emptyList(),
    /** True tra mezzanotte e l'ora di fine notte configurata (standby dormiente). */
    val isNightMode: Boolean = false,
    /** True while background LLM scans conversation log for durable user memories. */
    val isMemoryExtracting: Boolean = false,
    /** User poked left eye — force closed until cleared. */
    val eyeSquishLeft: Boolean = false,
    /** User poked right eye — force closed until cleared. */
    val eyeSquishRight: Boolean = false,
    /** Latest ML Kit desk occupancy (when presence monitor enabled). */
    val deskOccupancyState: com.example.mydeskrobot.domain.presence.DeskOccupancyState =
        com.example.mydeskrobot.domain.presence.DeskOccupancyState.UNKNOWN,
    val deskPresenceMonitorEnabled: Boolean = false,
    /**
     * Symbolic idle distraction overlay (headphones / book / away sign / pong).
     * Situational boredom only — does not change wellbeing valence.
     */
    val idleDistraction: IdleDistractionKind? = null,
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
