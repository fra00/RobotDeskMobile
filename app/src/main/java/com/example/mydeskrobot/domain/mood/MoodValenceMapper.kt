package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import java.util.Locale
import kotlin.math.abs

data class DerivedEmotion(
    val emotion: RobotEmotion,
    val intensity: Float,
)

/**
 * Maps persistent valence (+ reason overrides) to standby/base emotion for eyes and body.
 */
object MoodValenceMapper {

    fun derive(
        valence: Float,
        reason: MoodReason?,
    ): DerivedEmotion {
        when (reason) {
            MoodReason.NIGHT_TIME -> return DerivedEmotion(RobotEmotion.SLEEPING, 1.0f)
            MoodReason.IDLE_VERY_LONG -> return DerivedEmotion(RobotEmotion.DROWSY, 0.5f)
            MoodReason.IDLE_LONG -> return DerivedEmotion(RobotEmotion.BORED, boredIntensity(valence))
            MoodReason.EYE_POKE -> {
                return if (valence <= -0.15f) {
                    DerivedEmotion(RobotEmotion.ANGRY, angryIntensity(valence))
                } else {
                    DerivedEmotion(RobotEmotion.CONFUSED, 0.4f)
                }
            }
            MoodReason.REMINDER_URGENT -> return DerivedEmotion(RobotEmotion.SURPRISED, 0.6f)
            else -> Unit
        }

        return when {
            valence >= 0.28f -> DerivedEmotion(RobotEmotion.HAPPY, happyIntensity(valence))
            valence >= 0.15f -> DerivedEmotion(RobotEmotion.NEUTRAL, 0.55f)
            valence >= -0.12f -> DerivedEmotion(RobotEmotion.NEUTRAL, 0.5f)
            valence >= -0.28f -> DerivedEmotion(RobotEmotion.BORED, boredIntensity(valence))
            else -> DerivedEmotion(RobotEmotion.SAD, sadIntensity(valence))
        }
    }

    fun formatValence(value: Float): String {
        val sign = if (value >= 0f) "+" else ""
        return "$sign${"%.2f".format(Locale.US, value)}"
    }

    private fun happyIntensity(valence: Float): Float =
        (0.4f + (valence - 0.28f) * 0.8f).coerceIn(0.4f, 0.85f)

    private fun boredIntensity(valence: Float): Float =
        (0.35f - abs(valence + 0.2f) * 0.5f).coerceIn(0.25f, 0.4f)

    private fun sadIntensity(valence: Float): Float =
        (0.45f + abs(valence) * 0.3f).coerceIn(0.45f, 0.6f)

    private fun angryIntensity(valence: Float): Float =
        (0.55f + abs(valence) * 0.5f).coerceIn(0.55f, 0.85f)
}
