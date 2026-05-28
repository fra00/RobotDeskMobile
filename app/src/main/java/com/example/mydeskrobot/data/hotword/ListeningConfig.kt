package com.example.mydeskrobot.data.hotword

data class ListeningConfig(
    val wakePhrase: String,
    val exitPhrase: String,
    /** Pausa (ms) senza nuovo testo → frase completa, invio LLM. */
    val utterancePauseMs: Long,
    /** Silenzio (ms) con buffer vuoto → fine sessione, standby. */
    val sessionSilenceTimeoutMs: Long,
    /** Attesa dopo TTS prima di riaprire il microfono (evita eco). */
    val postTtsCooldownMs: Long,
)
