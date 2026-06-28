package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.domain.presence.AttentionCenteringPolicy
import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import com.example.mydeskrobot.integration.body.BodyJoint
import com.example.mydeskrobot.integration.body.BodyMove
import com.example.mydeskrobot.integration.body.BodyStatus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plans incremental centering steps toward the user face in frame.
 */
object AttentionCenteringPlanner {

    /** Inverted vs raw image offset (front camera / mount). */
    private const val DEFAULT_PAN_SIGN = -1

    private const val PAN_SPEED = 16
    private const val TILT_SPEED = 14

    private const val MAX_HORIZONTAL_STEP = 12
    private const val MAX_VERTICAL_STEP = 8
    private const val LARGE_OFFSET_THRESHOLD = 0.22f

    fun horizontalError(gaze: FaceGazeSnapshot): Float = abs(gaze.horizontalOffset)

    fun planHorizontalMove(
        gaze: FaceGazeSnapshot,
        status: BodyStatus? = null,
        panSign: Int = -1,
        tolerance: Float = AttentionCenteringPolicy.CENTER_TOLERANCE,
    ): BodyMove.Joint? {
        if (abs(gaze.horizontalOffset) <= tolerance) return null

        val horizontalStep = (gaze.horizontalOffset * MAX_HORIZONTAL_STEP * panSign).roundToInt()
            .coerceIn(-MAX_HORIZONTAL_STEP, MAX_HORIZONTAL_STEP)
        if (abs(horizontalStep) < 3) return null

        val joint = if (abs(gaze.horizontalOffset) >= LARGE_OFFSET_THRESHOLD) {
            BodyJoint.BASE_PAN
        } else {
            BodyJoint.DISPLAY_PAN
        }
        val current = status?.joints?.get(joint.apiName)?.position ?: 0
        val target = (current + horizontalStep).coerceIn(-BodyJoint.LIMIT_DEG, BodyJoint.LIMIT_DEG)
        if (target == current) return null

        return BodyMove.Joint(
            joint = joint,
            position = target,
            speed = PAN_SPEED,
        )
    }

    fun planVerticalMove(
        gaze: FaceGazeSnapshot,
        status: BodyStatus? = null,
        tolerance: Float = AttentionCenteringPolicy.CENTER_TOLERANCE,
    ): BodyMove.Joint? {
        if (abs(gaze.verticalOffset) <= tolerance) return null

        val verticalStep = (-gaze.verticalOffset * MAX_VERTICAL_STEP).roundToInt()
            .coerceIn(-MAX_VERTICAL_STEP, MAX_VERTICAL_STEP)
        if (abs(verticalStep) < 2) return null

        val current = status?.joints?.get(BodyJoint.HEAD_TILT.apiName)?.position ?: 0
        val target = (current + verticalStep).coerceIn(-BodyJoint.LIMIT_DEG, BodyJoint.LIMIT_DEG)
        if (target == current) return null

        return BodyMove.Joint(
            joint = BodyJoint.HEAD_TILT,
            position = target,
            speed = TILT_SPEED,
        )
    }

    fun planMoves(
        gaze: FaceGazeSnapshot,
        status: BodyStatus? = null,
        panSign: Int = -1,
        tolerance: Float = AttentionCenteringPolicy.CENTER_TOLERANCE,
    ): List<BodyMove.Joint> = buildList {
        planHorizontalMove(gaze, status, panSign, tolerance)?.let(::add)
        planVerticalMove(gaze, status, tolerance)?.let(::add)
    }

    /** Incremental pan when no face is in frame yet — sweep to find the user. */
    fun planScanPanMove(
        currentPan: Int,
        directionSign: Int,
    ): BodyMove.Joint? {
        val step = AttentionCenteringPolicy.SCAN_STEP_DEG * directionSign
        val target = (currentPan + step).coerceIn(-BodyJoint.LIMIT_DEG, BodyJoint.LIMIT_DEG)
        if (target == currentPan) return null

        return BodyMove.Joint(
            joint = BodyJoint.BASE_PAN,
            position = target,
            speed = PAN_SPEED,
        )
    }
}
