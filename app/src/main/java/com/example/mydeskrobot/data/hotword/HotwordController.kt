package com.example.mydeskrobot.data.hotword

import com.example.mydeskrobot.service.HotwordListeningService
import java.lang.ref.WeakReference

/**
 * Ponte tra ViewModel e [HotwordListeningService].
 * Durante la risposta del robot (LLM+TTS) lo STT è in pausa per evitare feedback audio.
 */
object HotwordController {

    @Volatile
    private var serviceRef: WeakReference<HotwordListeningService>? = null

    fun register(service: HotwordListeningService) {
        serviceRef = WeakReference(service)
    }

    fun unregister(service: HotwordListeningService) {
        serviceRef?.get()?.takeIf { it === service }?.let {
            serviceRef = null
        }
    }

    /** Pausa STT, cancella ascolto in corso e svuota il buffer frase. */
    fun beginAssistantTurn() {
        serviceRef?.get()?.beginAssistantTurn()
    }

    /** Svuota il buffer STT della frase in costruzione (dopo TTS / prima di nuovo input). */
    fun clearPendingPhrase() {
        serviceRef?.get()?.clearPendingPhrase()
    }

    fun endAssistantTurn(cooldownMs: Long, echoReferenceForCooldown: String? = null) {
        serviceRef?.get()?.endAssistantTurn(cooldownMs, echoReferenceForCooldown)
    }

    /**
     * Optional barge-in: reopens STT during TTS for voice interrupt (disabled in ConversationViewModel).
     * [lastAssistantResponse] feeds the echo filter when enabled.
     */
    fun beginBargeIn(lastAssistantResponse: String) {
        serviceRef?.get()?.beginBargeIn(lastAssistantResponse)
    }

    fun isRunning(): Boolean = serviceRef?.get()?.isDetecting() == true

    fun activateVoiceSession() {
        serviceRef?.get()?.activateVoiceSession()
    }

    /** Pause STT while user is on a phone call (released on [endPhoneCallHold]). */
    fun beginPhoneCallHold() {
        serviceRef?.get()?.beginPhoneCallHold()
    }

    fun endPhoneCallHold() {
        serviceRef?.get()?.endPhoneCallHold()
    }
}
