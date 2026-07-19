package com.example.mydeskrobot.integration.tool.remote

/**
 * Slices extracted article text for LLM context with hard caps.
 */
object FetchUrlContentSlice {

    const val DEFAULT_MAX_CHARS = 3500
    const val MAX_LLM_CONTENT_CHARS = 4500
    const val MIN_MAX_CHARS = 200

    fun parseMaxChars(raw: Any?): Int {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return DEFAULT_MAX_CHARS
        return value.coerceIn(MIN_MAX_CHARS, MAX_LLM_CONTENT_CHARS)
    }

    fun parseStartChar(raw: Any?): Int {
        val value = when (raw) {
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        } ?: return 0
        return value.coerceAtLeast(0)
    }

    fun slice(fullText: String, startChar: Int, maxChars: Int): FetchUrlSliceResult {
        val safeStart = startChar.coerceIn(0, fullText.length)
        val safeMax = maxChars.coerceIn(MIN_MAX_CHARS, MAX_LLM_CONTENT_CHARS)
        val end = (safeStart + safeMax).coerceAtMost(fullText.length)
        val content = fullText.substring(safeStart, end)
        val truncated = end < fullText.length
        return FetchUrlSliceResult(
            content = content,
            charsTotal = fullText.length,
            charsReturned = content.length,
            startChar = safeStart,
            truncated = truncated,
        )
    }
}

data class FetchUrlSliceResult(
    val content: String,
    val charsTotal: Int,
    val charsReturned: Int,
    val startChar: Int,
    val truncated: Boolean,
)
