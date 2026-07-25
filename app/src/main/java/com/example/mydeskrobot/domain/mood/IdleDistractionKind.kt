package com.example.mydeskrobot.domain.mood

/**
 * Symbolic idle distraction shown as a face overlay (no real media, reading, or games).
 */
enum class IdleDistractionKind {
    HEADPHONES,
    READING,
    AWAY,
    PONG,
    ;

    fun promptToken(): String = when (this) {
        HEADPHONES -> "musica"
        READING -> "lettura"
        AWAY -> "assente"
        PONG -> "pong"
    }
}
