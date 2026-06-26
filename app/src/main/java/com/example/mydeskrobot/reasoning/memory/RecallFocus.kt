package com.example.mydeskrobot.reasoning.memory

enum class RecallFocus {
    USER_FACTS,
    EPISODIC,
    MESSAGES,
    PLANNING,
    SPATIAL,
    GENERAL,
    ;

    companion object {
        fun fromJson(value: String?): RecallFocus? {
            val normalized = value?.trim()?.uppercase()?.replace(' ', '_') ?: return null
            return runCatching { valueOf(normalized) }.getOrNull()
        }
    }
}
