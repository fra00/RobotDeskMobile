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
    const val MAX_GAZE_AGE_MS = 2_500L
    const val MIN_CONFIDENCE = 0.45f
    const val MAX_LOOP_ITERATIONS = 5
    /** Minimum horizontal moves when face is visible before stopping (even if gaze lags). */
    const val MIN_CENTERING_MOVES = 2
    const val SETTLE_AFTER_MOVE_MS = 1_000L
    const val GAZE_WAIT_TIMEOUT_MS = 1_800L
    /** Extra beat when ML Kit has not refreshed yet after a joint move. */
    const val GAZE_RETRY_EXTRA_WAIT_MS = 700L
    const val GAZE_POLL_MS = 80L
    /** Offset magnitude increase that triggers pan sign flip. */
    const val WORSEN_EPSILON = 0.03f
    /** Pan step when scanning for a face not yet in frame (expand ±step, ±2·step, … from neutral). */
    const val SCAN_STEP_DEG = 14
    const val MAX_SCAN_MAGNITUDE_DEG = 42
    const val NEUTRAL_BASE_PAN = 0

    /** Absolute base_pan targets from neutral: +14, −14, +28, −28, … within [MAX_SCAN_MAGNITUDE_DEG]. */
    fun expandScanTargets(): List<Int> = buildList {
        var magnitude = SCAN_STEP_DEG
        while (magnitude <= MAX_SCAN_MAGNITUDE_DEG) {
            add(magnitude)
            add(-magnitude)
            magnitude += SCAN_STEP_DEG
        }
    }
}
