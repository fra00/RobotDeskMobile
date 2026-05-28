package com.example.mydeskrobot.domain.speech

sealed interface WakePhraseParseResult {
    data class Accepted(val query: String, val fullTranscript: String) : WakePhraseParseResult

    data class Rejected(
        val fullTranscript: String,
        val reason: RejectReason,
    ) : WakePhraseParseResult

    enum class RejectReason {
        MISSING_WAKE_PHRASE,
        EMPTY_QUERY_AFTER_WAKE_PHRASE,
    }
}
