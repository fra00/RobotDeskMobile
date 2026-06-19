package com.example.mydeskrobot.domain.pending

/**
 * Notification processed by the LLM while TTS was suppressed (work/call/meeting).
 * Available for optional voice replay; bot already used the content internally.
 */
data class UnannouncedNotification(
    val id: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val packageName: String,
    val receivedAtMillis: Long,
    val dedupKey: String,
    /** LLM reply text from silent processing, if any. */
    val robotSummary: String? = null,
) {
    fun displayBody(): String {
        robotSummary?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        val parts = listOfNotNull(
            title?.trim()?.takeIf { it.isNotBlank() },
            text?.trim()?.takeIf { it.isNotBlank() },
        )
        return parts.joinToString(" — ")
    }
}
