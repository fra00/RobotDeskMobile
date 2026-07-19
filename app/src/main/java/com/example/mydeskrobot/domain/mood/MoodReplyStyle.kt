package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * How the LLM should shape spoken replies based on visible face (ephemeral if active) or wellbeing.
 */
enum class MoodReplyStyle {
    /** Sad, angry, bored, or low valence — minimal words. */
    TERSE,
    /** Default desk-companion tone. */
    NORMAL,
    /** Happy / high valence — warmer, sunnier phrasing. */
    WARM,
}

object MoodReplyStyleResolver {

    private val TERSE_FACES = setOf(
        RobotEmotion.SAD,
        RobotEmotion.ANGRY,
        RobotEmotion.BORED,
        RobotEmotion.DROWSY,
        RobotEmotion.CONFUSED,
    )

    private val WARM_FACES = setOf(
        RobotEmotion.HAPPY,
        RobotEmotion.LOVING,
    )

    /**
     * @param visibleFace active ephemeral emotion if any; otherwise wellbeing face is used.
     */
    fun resolve(mood: RobotMood, visibleFace: RobotEmotion? = null): MoodReplyStyle {
        val face = visibleFace ?: mood.baseEmotion
        when (face) {
            in TERSE_FACES -> return MoodReplyStyle.TERSE
            in WARM_FACES -> return MoodReplyStyle.WARM
            else -> Unit
        }
        // No strong face signal (neutral/thinking/…): fall back to persistent valence.
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
