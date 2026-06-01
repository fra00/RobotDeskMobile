package com.example.mydeskrobot.domain.mood

/**
 * Formats autonomous robot mood for LLM system prompt injection.
 */
object MoodPromptFormatter {

    fun format(mood: RobotMood): String {
        val emotion = mood.baseEmotion.name.lowercase()
        val intensityPct = (mood.intensity * 100).toInt()
        val reasonLine = mood.reason?.let { formatReason(it) }.orEmpty()

        return buildString {
            appendLine("STATO ROBOT (autoritativo — rispetta in reply ed emotion):")
            appendLine("- Emozione: $emotion ($intensityPct%)")
            if (reasonLine.isNotBlank()) {
                appendLine("- Motivo: $reasonLine")
            }
            appendLine("- Regola: tono e emotion coerenti con questo stato; non contraddire (es. non dire \"benissimo\" se arrabbiato o annoiato).")
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
        MoodReason.REMINDER_URGENT -> "promemoria_urgente"
        MoodReason.USER_RETURNED -> "utente_tornato"
        MoodReason.HEARTBEAT_SUPPRESSED -> "heartbeat_soppresso"
    }
}
