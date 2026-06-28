package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.reasoning.model.CriticResult

/**
 * Parses critic LLM JSON: decision approve|modify|block.
 */
object HeartbeatCriticParser {

    private val DECISION_PATTERN = Regex(""""decision"\s*:\s*"(\w+)"""", RegexOption.IGNORE_CASE)
    private val REPLY_PATTERN = Regex(""""reply"\s*:\s*"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, originalProposal: String): CriticResult {
        val jsonText = extractJsonObject(raw) ?: return CriticResult.Approve(originalProposal)
        val decision = DECISION_PATTERN.find(jsonText)?.groupValues?.get(1)?.lowercase()
            ?: return CriticResult.Approve(originalProposal)

        return when (decision) {
            "approve" -> CriticResult.Approve(originalProposal)
            "block" -> CriticResult.Block
            "modify" -> {
                val modified = REPLY_PATTERN.find(jsonText)?.groupValues?.get(1)
                    ?.replace("\\\"", "\"")
                    ?.replace("\\n", "\n")
                    ?.trim()
                if (modified.isNullOrBlank()) {
                    CriticResult.Block
                } else {
                    CriticResult.Modify(modified)
                }
            }
            else -> CriticResult.Approve(originalProposal)
        }
    }

    private fun extractJsonObject(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            val end = trimmed.lastIndexOf('}')
            if (end > 0) return trimmed.substring(0, end + 1)
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }
        return null
    }
}
