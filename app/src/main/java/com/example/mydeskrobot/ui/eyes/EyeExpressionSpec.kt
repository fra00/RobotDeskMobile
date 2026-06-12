package com.example.mydeskrobot.ui.eyes

/**
 * Per-eye expression: sclera geometry, pupil, eyebrow, and motion hint.
 */
data class EyeExpressionSpec(
    val geometry: EyeGeometry,
    val pupil: PupilSpec = PupilSpec(),
    val eyebrow: EyebrowSpec = EyebrowSpec(),
    val motion: EyeMotion = EyeMotion.NONE,
    /** Motion strength multiplier (0–1), typically from mood intensity. */
    val motionAmplitude: Float = 1f,
)

data class PupilSpec(
    val offsetXFraction: Float = 0f,
    val offsetYFraction: Float = 0f,
    val radiusFraction: Float = 0.22f,
    val visible: Boolean = true,
    /** Max horizontal drift for [EyeMotion.PUPIL_DRIFT] (fraction of eye width). */
    val driftAmplitude: Float = 0f,
)

enum class EyebrowStyle {
    NONE,
    NEUTRAL,
    HAPPY_ARCH,
    ANGRY_V,
    SAD_DROP,
    SURPRISED_HIGH,
}

data class EyebrowSpec(
    val style: EyebrowStyle = EyebrowStyle.NONE,
    val thicknessFraction: Float = 0.08f,
    val liftFraction: Float = 0.14f,
)

enum class EyeMotion {
    NONE,
    SHAKE,
    BOUNCE,
    PUPIL_DRIFT,
    SLOW_DROOP,
}

/** Pair-level spec including blink/pulse flags from legacy [EyePairSpec]. */
data class EyePairExpressionSpec(
    val left: EyeExpressionSpec,
    val right: EyeExpressionSpec,
    val enableBlink: Boolean = true,
    val enableListeningPulse: Boolean = false,
    val enableSpeakingPulse: Boolean = false,
    val surprisedPop: Boolean = false,
)
