package com.example.mydeskrobot.domain.presence

/**
 * Normalized face position relative to frame center from ML Kit.
 * horizontalOffset: negative = face left of center, positive = face right.
 * verticalOffset: negative = face above center, positive = face below.
 */
data class FaceGazeSnapshot(
    val horizontalOffset: Float,
    val verticalOffset: Float,
    val confidence: Float,
    val capturedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(confidence in 0f..1f)
    }

    fun isCentered(tolerance: Float = AttentionCenteringPolicy.CENTER_TOLERANCE): Boolean =
        kotlin.math.abs(horizontalOffset) <= tolerance &&
            kotlin.math.abs(verticalOffset) <= tolerance
}

object AttentionCenteringPolicy {
    const val CENTER_TOLERANCE = 0.10f
    const val MIN_INTERVAL_MS = 20_000L
    const val MAX_GAZE_AGE_MS = 2_500L
    const val MIN_CONFIDENCE = 0.45f
    const val MAX_LOOP_ITERATIONS = 5
    const val SETTLE_AFTER_MOVE_MS = 550L
    const val GAZE_WAIT_TIMEOUT_MS = 900L
    const val GAZE_POLL_MS = 80L
    /** Offset magnitude increase that triggers pan sign flip. */
    const val WORSEN_EPSILON = 0.03f
    /** Pan step when scanning for a face not yet in frame. */
    const val SCAN_STEP_DEG = 14
    const val MAX_SCAN_STEPS = 4
}
