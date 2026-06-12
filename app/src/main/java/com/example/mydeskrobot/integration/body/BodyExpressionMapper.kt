package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.MoodReason
import com.example.mydeskrobot.domain.mood.RobotMood

/**
 * Maps mood transitions (SSOT) to deterministic body presets.
 * LLM body tools remain available for goal-driven chains.
 */
object BodyExpressionMapper {

    fun resolve(previous: RobotMood, current: RobotMood): List<BodyMove> {
        if (current.baseEmotion == RobotEmotion.SLEEPING && previous.baseEmotion != RobotEmotion.SLEEPING) {
            return listOf(BodyMove.SleepPose)
        }
        return when (current.reason) {
            MoodReason.EYE_POKE -> eyePokeMoves(current)
            MoodReason.USER_APOLOGY -> apologyMoves(current)
            MoodReason.IDLE_LONG ->
                if (current.baseEmotion == RobotEmotion.BORED && previous.baseEmotion == RobotEmotion.NEUTRAL) {
                    listOf(BodyMove.Joint(BodyJoint.DISPLAY_PAN, delta = 6, speed = 25))
                } else {
                    emptyList()
                }
            MoodReason.POSITIVE_INTERACTION ->
                if (current.baseEmotion == RobotEmotion.HAPPY) happyNod() else emptyList()
            else -> decayMoves(previous, current)
        }
    }

    private fun eyePokeMoves(current: RobotMood): List<BodyMove> = when (current.baseEmotion) {
        RobotEmotion.ANGRY -> {
            val awayDelta = if (current.intensity >= 0.8f) -15 else -12
            listOf(BodyMove.Joint(BodyJoint.DISPLAY_PAN, delta = awayDelta, speed = 35))
        }
        RobotEmotion.CONFUSED ->
            listOf(BodyMove.Joint(BodyJoint.HEAD_ROLL, delta = 8, speed = 30))
        else -> emptyList()
    }

    private fun apologyMoves(current: RobotMood): List<BodyMove> = when (current.baseEmotion) {
        RobotEmotion.NEUTRAL -> listOf(BodyMove.Home(speed = 35))
        RobotEmotion.CONFUSED ->
            listOf(BodyMove.Joint(BodyJoint.DISPLAY_PAN, delta = 10, speed = 30))
        else -> emptyList()
    }

    private fun happyNod(): List<BodyMove> = listOf(
        BodyMove.Joint(BodyJoint.HEAD_TILT, delta = 10, speed = 40, delayAfterMs = 350L),
        BodyMove.Joint(BodyJoint.HEAD_TILT, delta = -10, speed = 40),
    )

    private fun decayMoves(previous: RobotMood, current: RobotMood): List<BodyMove> {
        val annoyedBefore = previous.reason == MoodReason.EYE_POKE &&
            (previous.baseEmotion == RobotEmotion.ANGRY || previous.baseEmotion == RobotEmotion.CONFUSED)
        val relaxedNow = current.baseEmotion == RobotEmotion.NEUTRAL && current.reason == null
        if (annoyedBefore && relaxedNow) {
            return listOf(BodyMove.Home(speed = 30))
        }
        return emptyList()
    }
}
