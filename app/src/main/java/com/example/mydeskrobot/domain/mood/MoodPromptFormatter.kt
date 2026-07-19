package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Formats persistent robot wellbeing for LLM system prompt injection.
 */
object MoodPromptFormatter {

    fun format(mood: RobotMood, promptHints: List<String> = emptyList()): String {
        val emotion = mood.baseEmotion.name.lowercase()
        val intensityPct = (mood.intensity * 100).toInt()
        val reasonLine = mood.reason?.let { formatReason(it) }.orEmpty()
        val valenceLine = MoodValenceMapper.formatValence(mood.valence)
        val baselineLine = MoodValenceMapper.formatValence(mood.baseline)

        return buildString {
            appendLine("STATO ROBOT (autoritativo — benessere persistente):")
            appendLine("- Valenza: $valenceLine (baseline $baselineLine)")
            appendLine("- Emozione di fondo: $emotion ($intensityPct%)")
            if (reasonLine.isNotBlank()) {
                appendLine("- Motivo: $reasonLine")
            }
            if (mood.recentDeltas.isNotEmpty()) {
                val events = mood.recentDeltas.takeLast(3).joinToString(", ") { delta ->
                    "${MoodValenceMapper.formatValence(delta.delta)} ${delta.event}"
                }
                appendLine("- Ultimi eventi: $events")
            }
            appendLine("- Regola: emotion sul turno finale aggiorna occhi (effimero). Valenza di fondo sale con presenza utile, scende con noia/critiche; happy/loving routinari non alzano la valenza.")
            appendLine("- Default conversazione: emotion neutral o thinking; happy solo per eventi emotivi reali (elogio, affetto, buona notizia).")
            appendLine("- Critiche all'utente verso di te (imperfetto, deluso, arrabbiato): rispondi con tono adeguato e emotion sad o angry — non happy routinario.")
            promptHints.forEach { hint ->
                appendLine("- Contesto turno: $hint")
            }
            if (mood.reason == MoodReason.NIGHT_TIME) {
                appendLine("- Interazione notturna legittima: rispondi breve e stanco, senza tono colpevole verso l'utente.")
            }
            if (mood.reason == MoodReason.EYE_POKE) {
                appendLine("- Scuse sincere dell'utente possono ammorbidire il tono; non diventare subito entusiasta.")
            }
            val replyStyle = MoodReplyStyleResolver.resolve(mood)
            appendLine("- Profilo stile: ${replyStyle.name.lowercase()}")
            MoodReplyStyleResolver.promptLines(replyStyle).forEach { appendLine(it) }
        }.trim()
    }

    private fun formatReason(reason: MoodReason): String = when (reason) {
        MoodReason.EYE_POKE -> "poke_occhi"
        MoodReason.USER_APOLOGY -> "scusa_utente"
        MoodReason.IDLE_LONG -> "idle_lungo"
        MoodReason.IDLE_LISTENING -> "ascolto_hotword_senza_voce"
        MoodReason.CONVERSATION_FATIGUE -> "fatica_conversazione"
        MoodReason.VOICE_TURN_PRESENCE -> "presenza_vocale"
        MoodReason.IDLE_VERY_LONG -> "idle_molto_lungo"
        MoodReason.NIGHT_TIME -> "notte"
        MoodReason.TASK_COMPLETED -> "task_completato"
        MoodReason.LLM_EXPRESSION -> "espressione_llm"
    }
}
