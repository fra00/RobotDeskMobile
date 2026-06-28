package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.domain.model.RobotEmotion
import kotlin.math.roundToInt

/**
 * Maps LLM ephemeral emotions and mood display to closed head-first choreographies.
 */
object EmotionGestureMapper {

    fun resolve(emotion: RobotEmotion, intensity: Float = 0.55f): BodyChoreography? {
        val scale = intensity.coerceIn(0.35f, 1f)
        return when (emotion) {
            RobotEmotion.SAD -> sadGesture(scale)
            RobotEmotion.HAPPY, RobotEmotion.LOVING -> happyNod(scale)
            RobotEmotion.SURPRISED -> surprisedLookAround()
            RobotEmotion.CONFUSED -> confusedTilt(scale)
            RobotEmotion.ANGRY -> angryTurnAway(scale)
            RobotEmotion.BORED -> boredLookAround()
            RobotEmotion.SLEEPING -> BodyChoreography(
                steps = listOf(BodyMove.SleepPose),
                normalizeHeadBefore = false,
                returnHeadAfter = false,
            )
            RobotEmotion.NEUTRAL,
            RobotEmotion.LISTENING,
            RobotEmotion.SPEAKING,
            RobotEmotion.THINKING,
            RobotEmotion.WINK,
            RobotEmotion.DROWSY,
            -> null
        }
    }

    fun resolveMoodEmotionChange(
        previous: RobotEmotion,
        current: RobotEmotion,
        intensity: Float,
    ): BodyChoreography? {
        if (previous == current) return null
        return resolve(current, intensity)
    }

    private fun scaled(base: Int, intensity: Float): Int =
        (base * intensity.coerceIn(0.5f, 1f)).roundToInt().coerceAtLeast(3)

    private fun sadGesture(intensity: Float): BodyChoreography {
        val down = -scaled(8, intensity)
        return BodyChoreography(
            steps = listOf(
                BodyMove.Joint(BodyJoint.HEAD_TILT, position = down, speed = 25, delayAfterMs = 1_200L),
                BodyMove.Joint(BodyJoint.HEAD_TILT, position = 0, speed = 25),
            ),
        )
    }

    private fun happyNod(intensity: Float): BodyChoreography {
        val up = scaled(8, intensity)
        return BodyChoreography(
            steps = listOf(
                BodyMove.Joint(BodyJoint.HEAD_TILT, position = up, speed = 40, delayAfterMs = 350L),
                BodyMove.Joint(BodyJoint.HEAD_TILT, position = 0, speed = 40),
            ),
        )
    }

    private fun surprisedLookAround(): BodyChoreography = BodyChoreography(
        steps = listOf(
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 10, speed = 32, delayAfterMs = 250L),
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = -10, speed = 32, delayAfterMs = 250L),
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 0, speed = 32),
        ),
    )

    private fun confusedTilt(intensity: Float): BodyChoreography {
        val roll = scaled(6, intensity)
        return BodyChoreography(
            steps = listOf(
                BodyMove.Joint(BodyJoint.HEAD_ROLL, position = roll, speed = 28, delayAfterMs = 500L),
                BodyMove.Joint(BodyJoint.HEAD_ROLL, position = 0, speed = 28),
            ),
        )
    }

    private fun angryTurnAway(intensity: Float): BodyChoreography {
        val away = -scaled(12, intensity)
        return BodyChoreography(
            steps = listOf(
                BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = away, speed = 35),
            ),
            returnHeadAfter = false,
        )
    }

    private fun boredLookAround(): BodyChoreography = BodyChoreography(
        steps = listOf(
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 10, speed = 22, delayAfterMs = 450L),
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = -10, speed = 22, delayAfterMs = 450L),
            BodyMove.Joint(BodyJoint.DISPLAY_PAN, position = 0, speed = 22),
        ),
    )
}
