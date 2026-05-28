package com.example.mydeskrobot.domain.speech

/**
 * Rileva la frase di uscita nella trascrizione (case-insensitive).
 */
class ExitPhraseMatcher(
    private val exitPhrase: String,
    private val ignoreCase: Boolean = true,
) {

    init {
        require(exitPhrase.isNotBlank()) { "exitPhrase must not be blank" }
    }

    fun parse(transcript: String): ExitPhraseParseResult {
        val text = transcript.trim()
        val phrase = exitPhrase.trim()

        if (text.equals(phrase, ignoreCase = ignoreCase)) {
            return ExitPhraseParseResult.ExitOnly
        }

        val prefix = "$phrase "
        if (text.startsWith(prefix, ignoreCase = ignoreCase)) {
            return ExitPhraseParseResult.ExitOnly
        }

        val suffix = " $phrase"
        if (text.endsWith(suffix, ignoreCase = ignoreCase)) {
            val content = text.dropLast(suffix.length).trim()
            return if (content.isEmpty()) ExitPhraseParseResult.ExitOnly
            else ExitPhraseParseResult.ContentThenExit(content)
        }

        val wrapped = " $phrase "
        val index = text.indexOf(wrapped, ignoreCase = ignoreCase)
        if (index >= 0) {
            val content = text.substring(0, index).trim()
            return if (content.isEmpty()) ExitPhraseParseResult.ExitOnly
            else ExitPhraseParseResult.ContentThenExit(content)
        }

        return ExitPhraseParseResult.NotExit
    }
}

sealed interface ExitPhraseParseResult {
    data object NotExit : ExitPhraseParseResult
    data object ExitOnly : ExitPhraseParseResult
    data class ContentThenExit(val content: String) : ExitPhraseParseResult
}
