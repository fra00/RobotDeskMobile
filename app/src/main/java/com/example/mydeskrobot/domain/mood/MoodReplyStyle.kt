package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * How the LLM should shape spoken replies based on persistent wellbeing.
 */
enum class MoodReplyStyle {
    /** Sad, angry, or low valence — minimal words. */
    TERSE,
    /** Default desk-companion tone. */
    NORMAL,
    /** Happy / high valence — warmer, sunnier phrasing. */
    WARM,
}

object MoodReplyStyleResolver {

    fun resolve(mood: RobotMood): MoodReplyStyle {
        when (mood.baseEmotion) {
            RobotEmotion.SAD,
            RobotEmotion.ANGRY,
            -> return MoodReplyStyle.TERSE
            RobotEmotion.HAPPY,
            RobotEmotion.LOVING,
            -> return MoodReplyStyle.WARM
            else -> Unit
        }
        return when {
            mood.valence >= 0.28f -> MoodReplyStyle.WARM
            mood.valence <= -0.12f -> MoodReplyStyle.TERSE
            else -> MoodReplyStyle.NORMAL
        }
    }

    fun promptLines(style: MoodReplyStyle): List<String> = when (style) {
        MoodReplyStyle.TERSE -> listOf(
            "STILE RISPOSTA (vincolante — sintetico):",
            "- Massimo 1–2 frasi brevi; solo l'essenziale.",
            "- Tono basso o secco se arrabbiato; soft ma senza sfociare se triste.",
            "- Niente entusiasmo, emoji, convenevoli lunghi, né spiegazioni extra.",
            "- Stesso contenuto informativo, meno parole.",
        )
        MoodReplyStyle.NORMAL -> listOf(
            "STILE RISPOSTA (normale):",
            "- Breve e parlato come al solito (colloquiale, chiaro).",
            "- Né freddo né eccessivamente solare — equilibrato.",
        )
        MoodReplyStyle.WARM -> listOf(
            "STILE RISPOSTA (cordiale):",
            "- Tono solare e leggermente più caldo del solito — come un coinquilino di buon umore, non un call center.",
            "- Calore umano ok (\"dai\", \"figurati\", \"bene così\"); evita formule da assistente (\"con piacere\", \"a disposizione\").",
            "- Resta conciso: cordialità ≠ monologo; 2–3 frasi al massimo salvo richiesta esplicita di dettaglio.",
        )
    }
}
