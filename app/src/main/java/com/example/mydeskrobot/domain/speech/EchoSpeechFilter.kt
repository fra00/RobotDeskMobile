package com.example.mydeskrobot.domain.speech

/**
 * Evita di trattare come domanda utente il testo trascritto dalla voce del robot (TTS).
 */
object EchoSpeechFilter {

    fun isLikelyAssistantEcho(transcript: String, lastAssistantResponse: String?): Boolean {
        val heard = normalize(transcript)
        val spoken = normalize(lastAssistantResponse ?: return false)
        if (heard.isBlank() || spoken.isBlank()) return false

        if (heard == spoken) return true
        if (heard.length >= 8 && spoken.contains(heard)) return true
        if (spoken.length >= 8 && heard.contains(spoken)) return true

        val heardWords = heard.split(' ').filter { it.length > 2 }.toSet()
        val spokenWords = spoken.split(' ').filter { it.length > 2 }.toSet()
        if (heardWords.isEmpty() || spokenWords.isEmpty()) return false

        val overlap = heardWords.intersect(spokenWords).size
        val ratio = overlap.toFloat() / minOf(heardWords.size, spokenWords.size)
        return ratio >= 0.6f
    }

    /**
     * Se la trascrizione inizia con l'eco del TTS, restituisce solo la parte utente.
     * Utile quando STT concatena risposta robot + nuova domanda in un unico risultato.
     */
    fun stripLeadingAssistantEcho(transcript: String, lastAssistantResponse: String?): String {
        val trimmed = transcript.trim()
        val ref = lastAssistantResponse?.trim().orEmpty()
        if (trimmed.isEmpty() || ref.isEmpty()) return trimmed

        val heardWords = trimmed.split(Regex("\\s+"))
        val refWords = ref.split(Regex("\\s+"))
        if (heardWords.isEmpty() || refWords.isEmpty()) return trimmed

        var matchedPrefix = 0
        while (matchedPrefix < heardWords.size && matchedPrefix < refWords.size) {
            if (normalize(heardWords[matchedPrefix]) != normalize(refWords[matchedPrefix])) break
            matchedPrefix++
        }

        if (matchedPrefix >= minOf(refWords.size, maxOf(3, refWords.size / 2))) {
            return heardWords.drop(matchedPrefix).joinToString(" ").trim()
        }

        val heardNorm = normalize(trimmed)
        val spokenNorm = normalize(ref)
        if (spokenNorm.length >= 8 && heardNorm.startsWith(spokenNorm)) {
            val ratio = spokenNorm.length.toFloat() / heardNorm.length.coerceAtLeast(1)
            val dropChars = (trimmed.length * ratio).toInt().coerceIn(0, trimmed.length)
            return trimmed.drop(dropChars).trim()
        }

        return trimmed
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-zàèéìòù0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
