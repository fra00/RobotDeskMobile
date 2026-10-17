package com.example.mydeskrobot.domain.proxy

enum class DeviceMode {
    ROBOT,
    PROXY,
    ;

    companion object {
        fun fromStorage(raw: String?): DeviceMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ROBOT
    }
}
