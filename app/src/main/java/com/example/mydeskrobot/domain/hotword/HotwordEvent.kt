package com.example.mydeskrobot.domain.hotword

sealed interface HotwordEvent {
    data class SessionStarted(val initialText: String?) : HotwordEvent

    /** Testo in costruzione nella frase corrente (prima dei 5s di pausa). */
    data class UtteranceInProgress(val text: String) : HotwordEvent

    /** Pausa di 5s: frase completa da inviare al LLM. */
    data class UtteranceReadyForLlm(val phrase: String) : HotwordEvent

    /** Utente parla mentre il robot sta leggendo la risposta (TTS). */
    data class SpeechInterrupted(val transcript: String) : HotwordEvent

    data class SessionEnded(val reason: SessionEndReason) : HotwordEvent

    data object EngineStopped : HotwordEvent
}

enum class SessionEndReason {
    EXIT_PHRASE,
    SILENCE_TIMEOUT,
}
