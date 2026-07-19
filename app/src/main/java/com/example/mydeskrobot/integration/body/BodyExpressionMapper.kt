package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.RobotMood

/**
 * Maps mood transitions (SSOT) to deterministic body presets.
 * LLM body tools remain available for goal-driven chains.
 */
object BodyExpressionMapper {

    /**
     * Quiet look-around while bored/idle (heartbeat MICRO tick, no mood transition).
     */
    fun resolveMicroTick(mood: RobotMood, idleMinutes: Long): BodyChoreography? {
        if (idleMinutes < 15) return null
        if (mood.baseEmotion != RobotEmotion.BORED && mood.baseEmotion != RobotEmotion.DROWSY) {
            return null
        }
        return EmotionGestureMapper.resolve(RobotEmotion.BORED, mood.intensity)
    }

    fun resolve(previous: RobotMood, current: RobotMood): BodyChoreography? {
        if (current.baseEmotion == RobotEmotion.SLEEPING && previous.baseEmotion != RobotEmotion.SLEEPING) {
            return BodyChoreography(
                steps = listOf(BodyMove.SleepPose),
                normalizeHeadBefore = false,
                returnHeadAfter = false,
            )
        }

        val reasonBased = when (current.reason) {
            MoodReason.EYE_POKE -> eyePokeChoreography(current)
            MoodReason.USER_APOLOGY -> apologyChoreography(current)
            MoodReason.IDLE_LONG ->
                if (current.baseEmotion == RobotEmotion.BORED && previous.baseEmotion == RobotEmotion.NEUTRAL) {
                    idleBoredFidget()
                } else {
                    null
                }
            MoodReason.LLM_EXPRESSION ->
                if (current.baseEmotion == RobotEmotion.HAPPY) {
                    EmotionGestureMapper.resolve(RobotEmotion.HAPPY, current.intensity)
                } else {
                    null
                }
            else -> decayChoreography(previous, current)
        }
        if (reasonBased != null) return reasonBased

        if (current.baseEmotion != previous.baseEmotion) {
            return EmotionGestureMapper.resolveMoodEmotionChange(
                previous = previous.baseEmotion,
                current = current.baseEmotion,
                intensity = current.intensity,
            )
        }
        return null
    }

    private fun eyePokeChoreography(current: RobotMood): BodyChoreography? = when (current.baseEmotion) {
        RobotEmotion.ANGRY -> {
            val away = if (current.intensity >= 0.8f) -15 else -12
            BodyChoreography(
                steps = listOf(
                    BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = away, speed = 35),
                ),
                returnHeadAfter = false,
            )
        }
        RobotEmotion.CONFUSED -> EmotionGestureMapper.resolve(RobotEmotion.CONFUSED, current.intensity)
        else -> null
    }

    private fun apologyChoreography(current: RobotMood): BodyChoreography? = when (current.baseEmotion) {
        RobotEmotion.NEUTRAL -> BodyChoreography(
            steps = listOf(BodyMove.Home(speed = 35)),
            normalizeHeadBefore = false,
        )
        RobotEmotion.CONFUSED -> BodyChoreography(
            steps = listOf(
                BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 10, speed = 30, delayAfterMs = 400L),
                BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 0, speed = 30),
            ),
        )
        else -> null
    }

    private fun idleBoredFidget(): BodyChoreography = BodyChoreography(
        steps = listOf(
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 6, speed = 25, delayAfterMs = 400L),
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 0, speed = 25),
        ),
    )

    private fun decayChoreography(previous: RobotMood, current: RobotMood): BodyChoreography? {
        val annoyedBefore = previous.reason == MoodReason.EYE_POKE &&
            (previous.baseEmotion == RobotEmotion.ANGRY || previous.baseEmotion == RobotEmotion.CONFUSED)
        val relaxedNow = current.baseEmotion == RobotEmotion.NEUTRAL && current.reason == null
        if (annoyedBefore && relaxedNow) {
            return BodyChoreography(
                steps = listOf(BodyMove.Home(speed = 30)),
                normalizeHeadBefore = false,
            )
        }
        return null
    }
}
