package com.example.mydeskrobot.data.hotword

data class ListeningConfig(
    val wakePhrase: String,
    val exitPhrase: String,
    /** Silence after last transcript/final before [HotwordEvent.UtteranceReadyForLlm]. */
    val endOfUtteranceMs: Long,
    /** Provider: close listen segment after this silence (no new partials). */
    val segmentSilenceMs: Long,
    /** Silence with empty buffer → end session, return to standby. */
    val sessionSilenceTimeoutMs: Long,
    /** Wait after TTS before reopening microphone (anti-echo). */
    val postTtsCooldownMs: Long,
) {
    companion object {
        /** Provider segment silence as fraction of end-of-utterance (balanced ~1.8s total). */
        fun segmentSilenceFor(endOfUtteranceMs: Long): Long =
            (endOfUtteranceMs * 55 / 100).coerceIn(800L, 1_200L)
    }
}
