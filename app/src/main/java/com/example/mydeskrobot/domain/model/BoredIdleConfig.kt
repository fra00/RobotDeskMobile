package com.example.mydeskrobot.domain.model

/**
 * Timer per l'espressione "annoiato" in standby (microfono attivo, nessuna wake word).
 */
data class BoredIdleConfig(
    /** Attesa in standby prima della prima espressione annoiata. */
    val idleBeforeBoredMs: Long,
    /** Durata dell'espressione BORED prima di tornare NEUTRAL. */
    val boredDurationMs: Long,
    /** Pausa tra un'episodio annoiato e il successivo (sempre in standby). */
    val repeatIntervalMs: Long,
)
