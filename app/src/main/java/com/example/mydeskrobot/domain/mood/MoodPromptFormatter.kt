package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion

/**
 * Formats persistent robot wellbeing for LLM system prompt injection.
 */
object MoodPromptFormatter {

    fun format(mood: RobotMood): String {
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
            appendLine("- Regola: il campo JSON emotion è espressione IMMEDIATA del turno (effimera); il benessere qui sopra è lo stato di fondo.")
            appendLine("- Allinea tono e reply al benessere; emotion può divergere brevemente (es. angry teatrale con valenza positiva).")
            if (mood.reason == MoodReason.NIGHT_TIME) {
                appendLine("- Interazione notturna legittima: rispondi breve e stanco, senza tono colpevole verso l'utente.")
            }
            if (mood.reason == MoodReason.EYE_POKE) {
                appendLine("- Scuse sincere dell'utente possono ammorbidire il tono; non diventare subito entusiasta.")
            }
        }.trim()
    }

    private fun formatReason(reason: MoodReason): String = when (reason) {
        MoodReason.EYE_POKE -> "poke_occhi"
        MoodReason.USER_APOLOGY -> "scusa_utente"
        MoodReason.IDLE_LONG -> "idle_lungo"
        MoodReason.IDLE_VERY_LONG -> "idle_molto_lungo"
        MoodReason.NIGHT_TIME -> "notte"
        MoodReason.POSITIVE_INTERACTION -> "interazione_positiva"
        MoodReason.NEGATIVE_INTERACTION -> "interazione_negativa"
        MoodReason.TASK_COMPLETED -> "task_completato"
        MoodReason.REMINDER_URGENT -> "promemoria_urgente"
        MoodReason.USER_RETURNED -> "utente_tornato"
        MoodReason.HEARTBEAT_SUPPRESSED -> "heartbeat_soppresso"
    }
}
