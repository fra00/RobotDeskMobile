package com.example.mydeskrobot.domain.llm

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Converte il campo [emotion] del JSON LLM in [RobotEmotion].
 * Valori non riconosciuti → null (nessun cambio emozione).
 */
object LlmEmotionMapper {

    fun fromLlmValue(raw: String?): RobotEmotion? {
        return when (raw?.trim()?.lowercase()) {
            "happy", "felice", "contento", "gioia" -> RobotEmotion.HAPPY
            "sad", "triste", "dispiaciuto", "tristezza" -> RobotEmotion.SAD
            "angry", "arrabbiato", "rabbia" -> RobotEmotion.ANGRY
            "surprised", "sorpreso", "sorpresa" -> RobotEmotion.SURPRISED
            "confused", "confuso", "confusione" -> RobotEmotion.CONFUSED
            "neutral", "neutro" -> RobotEmotion.NEUTRAL
            else -> null
        }
    }
}
