package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.EphemeralExpression
import com.example.mydeskrobot.domain.mood.RobotMood

/**
 * Resolves which emotion the UI should show: ephemeral LLM expression overrides wellbeing.
 */
object DisplayEmotionResolver {

    fun resolve(
        wellbeing: RobotMood,
        ephemeral: EphemeralExpression?,
        phase: ConversationPhase,
        isNightMode: Boolean,
        now: Long = System.currentTimeMillis(),
    ): RobotEmotion = when (phase) {
        is ConversationPhase.Thinking,
        is ConversationPhase.CapturingImage,
        -> RobotEmotion.THINKING
        is ConversationPhase.Speaking,
        is ConversationPhase.ActiveListening,
        -> if (ephemeral?.isActive(now) == true) ephemeral.emotion else wellbeing.baseEmotion
        is ConversationPhase.WaitingForHotword -> resolveStandbyEmotion(wellbeing, isNightMode)
        is ConversationPhase.Idle -> RobotEmotion.NEUTRAL
        is ConversationPhase.Error -> wellbeing.baseEmotion
    }

    fun resolveIntensity(
        wellbeing: RobotMood,
        ephemeral: EphemeralExpression?,
        phase: ConversationPhase,
        now: Long = System.currentTimeMillis(),
    ): Float {
        if (phase is ConversationPhase.Speaking || phase is ConversationPhase.ActiveListening) {
            if (ephemeral?.isActive(now) == true) return ephemeral.intensity
        }
        return wellbeing.intensity
    }

    private fun resolveStandbyEmotion(wellbeing: RobotMood, isNightMode: Boolean): RobotEmotion {
        if (isNightMode) {
            if (wellbeing.baseEmotion == RobotEmotion.SLEEPING ||
                wellbeing.baseEmotion == RobotEmotion.DROWSY
            ) {
                return wellbeing.baseEmotion
            }
            return RobotEmotion.SLEEPING
        }
        return wellbeing.baseEmotion
    }
}
