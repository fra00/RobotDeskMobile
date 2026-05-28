package com.example.mydeskrobot.domain.model

/**
 * Risposta strutturata dell'assistente LLM.
 * [emotion] è opzionale: se assente, l'app mantiene l'emozione corrente.
 * [imageRequired] true = l'app deve scattare una foto e inviare un secondo turno con immagine.
 */
data class LlmAssistantReply(
    val text: String,
    val emotion: RobotEmotion? = null,
    val imageRequired: Boolean = false,
)
