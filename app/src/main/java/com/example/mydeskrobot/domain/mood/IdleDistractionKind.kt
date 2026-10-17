package com.example.mydeskrobot.domain.mood

/**
 * Idle distraction shown as a face overlay (UI scene).
 * For the LLM / persona this is a **real** activity the robot was doing, not "fake mimica".
 * (No Spotify / real book content — diegetic framing only.)
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

    /** Present continuous — while the scene is active. */
    fun promptActivityOngoing(): String = when (this) {
        HEADPHONES -> "stai ascoltando musica con le cuffie"
        READING -> "stai leggendo"
        AWAY -> "sei uscito un attimo (cartello «Torno subito»)"
        PONG -> "stai giocando a Pong"
    }

    /** Past — shortly after the scene ended. */
    fun promptActivityRecent(): String = when (this) {
        HEADPHONES -> "stavi ascoltando musica con le cuffie"
        READING -> "stavi leggendo"
        AWAY -> "eri uscito un attimo («Torno subito»)"
        PONG -> "stavi giocando a Pong"
    }
}
