package com.example.mydeskrobot.ui.eyes

object ExpressionIntensity {
    fun coerce(intensity: Float): Float = intensity.coerceIn(0f, 1f)

    fun lerp(min: Float, max: Float, intensity: Float): Float {
        val t = coerce(intensity)
        return min + (max - min) * t
    }
}
