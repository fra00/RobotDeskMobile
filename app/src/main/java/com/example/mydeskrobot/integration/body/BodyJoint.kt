package com.example.mydeskrobot.integration.body

enum class BodyJoint(val apiName: String) {
    BASE_PAN("base_pan"),
    HEAD_ROLL("head_roll"),
    HEAD_TILT("head_tilt"),
    DISPLAY_PAN("display_pan"),
    ;

    companion object {
        const val LIMIT_DEG = 45

        fun fromApiName(name: String): BodyJoint? =
            entries.firstOrNull { it.apiName.equals(name.trim(), ignoreCase = true) }

        fun clampDegrees(value: Int): Int = value.coerceIn(-LIMIT_DEG, LIMIT_DEG)
    }
}
