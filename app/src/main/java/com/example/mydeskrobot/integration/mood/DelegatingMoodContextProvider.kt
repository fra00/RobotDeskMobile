package com.example.mydeskrobot.integration.mood

import com.example.mydeskrobot.domain.mood.EphemeralExpression
import com.example.mydeskrobot.domain.mood.HumanVoicePrompt
import com.example.mydeskrobot.domain.mood.IdleDistractionKind
import com.example.mydeskrobot.domain.mood.MoodPromptFormatter
import com.example.mydeskrobot.domain.mood.RobotMood
import com.example.mydeskrobot.reasoning.MoodContextProvider

/**
 * Reads live mood from snapshot providers wired by [com.example.mydeskrobot.presentation.conversation.ConversationViewModel].
 */
class DelegatingMoodContextProvider : MoodContextProvider {

    var snapshotProvider: () -> RobotMood = { RobotMood.NEUTRAL }

    var ephemeralProvider: () -> EphemeralExpression? = { null }

    var promptHintsProvider: () -> List<String> = { emptyList() }

    var idleDistractionProvider: () -> IdleDistractionKind? = { null }

    override suspend fun buildContextSection(): String = buildString {
        append(
            MoodPromptFormatter.format(
                mood = snapshotProvider(),
                promptHints = promptHintsProvider(),
                ephemeral = ephemeralProvider(),
                idleDistraction = idleDistractionProvider(),
            ),
        )
        append("\n\n")
        append(HumanVoicePrompt.section())
    }
}
