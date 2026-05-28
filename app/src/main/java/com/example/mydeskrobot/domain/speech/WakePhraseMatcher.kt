package com.example.mydeskrobot.domain.speech

/**
 * Validates that speech transcription starts with the configured wake phrase
 * and extracts the user question that follows it.
 */
class WakePhraseMatcher(
    private val wakePhrase: String,
    private val ignoreCase: Boolean = true,
) {

    init {
        require(wakePhrase.isNotBlank()) { "wakePhrase must not be blank" }
    }

    fun parse(transcript: String): WakePhraseParseResult {
        val normalizedTranscript = transcript.trim()
        val normalizedPhrase = wakePhrase.trim()

        if (!normalizedTranscript.startsWith(normalizedPhrase, ignoreCase = ignoreCase)) {
            return WakePhraseParseResult.Rejected(
                fullTranscript = normalizedTranscript,
                reason = WakePhraseParseResult.RejectReason.MISSING_WAKE_PHRASE,
            )
        }

        val query = normalizedTranscript
            .substring(normalizedPhrase.length)
            .trimLeadingPunctuation()
            .trim()

        if (query.isEmpty()) {
            return WakePhraseParseResult.Rejected(
                fullTranscript = normalizedTranscript,
                reason = WakePhraseParseResult.RejectReason.EMPTY_QUERY_AFTER_WAKE_PHRASE,
            )
        }

        return WakePhraseParseResult.Accepted(
            query = query,
            fullTranscript = normalizedTranscript,
        )
    }

    /**
     * After hotword detection: use extracted query if the user repeated the wake phrase,
     * otherwise keep the full transcript as the question.
     */
    fun extractQueryOrFull(transcript: String): String {
        return when (val result = parse(transcript)) {
            is WakePhraseParseResult.Accepted -> result.query
            is WakePhraseParseResult.Rejected -> transcript.trim()
        }
    }

    private fun String.trimLeadingPunctuation(): String {
        return dropWhile { it in LEADING_PUNCTUATION }
    }

    companion object {
        private val LEADING_PUNCTUATION = charArrayOf(',', '.', ':', ';', '!', '?', ' ')
    }
}
