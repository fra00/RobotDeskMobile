package com.example.mydeskrobot.domain.llm

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Converte il campo [emotion] del JSON LLM in [RobotEmotion].
 * Valori non riconosciuti → null (nessun cambio emozione).
 */
object LlmEmotionMapper {

    fun fromLlmValue(raw: String?): RobotEmotion? {
        return when (raw?.trim()?.lowercase()) {
            "happy", "felice", "contento", "gioia", "allegro", "sorridente" -> RobotEmotion.HAPPY
            "loving", "love", "innamorato", "innamorata", "affettuoso" -> RobotEmotion.LOVING
            "wink", "occhiolino", "strizione", "strizione_occhio" -> RobotEmotion.WINK
            "sad", "triste", "dispiaciuto", "tristezza", "malinconico" -> RobotEmotion.SAD
            "angry", "arrabbiato", "rabbia", "furioso" -> RobotEmotion.ANGRY
            "surprised", "sorpreso", "sorpresa", "stupito" -> RobotEmotion.SURPRISED
            "confused", "confuso", "confusione", "perplesso" -> RobotEmotion.CONFUSED
            "neutral", "neutro", "indifferente" -> RobotEmotion.NEUTRAL
            "thinking", "pensieroso", "pensiero" -> RobotEmotion.THINKING
            "bored", "noia", "annoiato", "annoio" -> RobotEmotion.BORED
            "sleeping", "sleep", "dormiente", "dormi",
            "closed", "eyes_closed", "occhi_chiusi" -> RobotEmotion.SLEEPING
            "drowsy", "assonnato", "assonnata", "stanco", "stanca" -> RobotEmotion.DROWSY
            else -> null
        }
    }
}
