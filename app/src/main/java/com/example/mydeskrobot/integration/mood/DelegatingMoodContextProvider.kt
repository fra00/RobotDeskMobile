package com.example.mydeskrobot.integration.mood

import com.example.mydeskrobot.domain.mood.HumanVoicePrompt
import com.example.mydeskrobot.domain.mood.MoodPromptFormatter
import com.example.mydeskrobot.domain.mood.RobotMood
import com.example.mydeskrobot.reasoning.MoodContextProvider

/**
 * Reads live mood from a snapshot provider wired by [com.example.mydeskrobot.presentation.conversation.ConversationViewModel].
 */
class DelegatingMoodContextProvider : MoodContextProvider {

    var snapshotProvider: () -> RobotMood = { RobotMood.NEUTRAL }

    var promptHintsProvider: () -> List<String> = { emptyList() }

    override suspend fun buildContextSection(): String = buildString {
        append(MoodPromptFormatter.format(snapshotProvider(), promptHintsProvider()))
        append("\n\n")
        append(HumanVoicePrompt.section())
    }
}
