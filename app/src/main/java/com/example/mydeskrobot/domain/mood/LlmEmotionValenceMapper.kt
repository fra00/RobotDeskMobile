package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Maps final LLM [RobotEmotion] on a completed turn to persistent wellbeing deltas.
 * User tone is interpreted by the LLM (emotion field), not keyword lists.
 */
object LlmEmotionValenceMapper {

    fun valenceDelta(
        emotion: RobotEmotion?,
        tier: LlmEmotionValenceTier = LlmEmotionValenceTier.FULL,
    ): Float? {
        if (tier == LlmEmotionValenceTier.NONE) return null
        if (tier == LlmEmotionValenceTier.ROUTINE &&
            (emotion == RobotEmotion.HAPPY || emotion == RobotEmotion.LOVING)
        ) {
            return null
        }
        return baseValenceDelta(emotion)
    }

    private fun baseValenceDelta(emotion: RobotEmotion?): Float? = when (emotion) {
        RobotEmotion.SAD -> MoodValenceConfig.LLM_SAD_DELTA
        RobotEmotion.ANGRY -> MoodValenceConfig.LLM_ANGRY_DELTA
        RobotEmotion.CONFUSED -> MoodValenceConfig.LLM_CONFUSED_DELTA
        RobotEmotion.HAPPY -> MoodValenceConfig.LLM_HAPPY_DELTA
        RobotEmotion.LOVING -> MoodValenceConfig.LLM_LOVING_DELTA
        RobotEmotion.BORED -> MoodValenceConfig.LLM_BORED_DELTA
        RobotEmotion.NEUTRAL,
        RobotEmotion.THINKING,
        RobotEmotion.SURPRISED,
        RobotEmotion.WINK,
        RobotEmotion.SLEEPING,
        RobotEmotion.DROWSY,
        RobotEmotion.LISTENING,
        RobotEmotion.SPEAKING,
        null -> null
    }

    fun hasNegativeValenceImpact(emotion: RobotEmotion?): Boolean =
        baseValenceDelta(emotion)?.let { it < -0.001f } == true
}
