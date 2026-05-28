package com.example.mydeskrobot.domain.speech

/**
 * Removes markdown formatting from text before TTS playback.
 * TTS engines read markdown characters literally (e.g., "asterisk asterisk"),
 * so we need to strip them for natural speech.
 */
object MarkdownStripper {

    /**
     * Strips common markdown formatting and JSON residues from text.
     * 
     * Handles:
     * - Bold: **text** or __text__
     * - Italic: *text* or _text_
     * - Headers: # ## ### etc.
     * - Code inline: `code`
     * - Code blocks: ```code```
     * - Links: [text](url)
     * - Lists: - item, * item, 1. item
     * - Blockquotes: > text
     * - Horizontal rules: --- or ***
     * - JSON residues from malformed LLM responses
     */
    fun strip(text: String): String {
        var result = text
        
        // Remove JSON residues that may have leaked through parsing
        result = stripJsonResidues(result)

        // Remove code blocks first (```...```)
        result = result.replace(Regex("```[\\s\\S]*?```"), "")

        // Remove inline code (`code`)
        result = result.replace(Regex("`([^`]+)`")) { it.groupValues[1] }

        // Remove links [text](url) -> text
        result = result.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)")) { it.groupValues[1] }

        // Remove images ![alt](url)
        result = result.replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), "")

        // Remove bold **text** or __text__
        result = result.replace(Regex("\\*\\*([^*]+)\\*\\*")) { it.groupValues[1] }
        result = result.replace(Regex("__([^_]+)__")) { it.groupValues[1] }

        // Remove italic *text* or _text_ (single)
        result = result.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)")) { it.groupValues[1] }
        result = result.replace(Regex("(?<!_)_([^_]+)_(?!_)")) { it.groupValues[1] }

        // Remove headers (# ## ### etc.) at line start
        result = result.replace(Regex("(?m)^#{1,6}\\s*"), "")

        // Remove blockquotes (> ) at line start
        result = result.replace(Regex("(?m)^>\\s*"), "")

        // Remove unordered list markers (- or * at line start)
        result = result.replace(Regex("(?m)^[\\-\\*]\\s+"), "")

        // Remove ordered list markers (1. 2. etc.)
        result = result.replace(Regex("(?m)^\\d+\\.\\s+"), "")

        // Remove horizontal rules (--- or ***)
        result = result.replace(Regex("(?m)^[\\-\\*]{3,}\\s*$"), "")

        // Remove strikethrough ~~text~~
        result = result.replace(Regex("~~([^~]+)~~")) { it.groupValues[1] }

        // Clean up multiple spaces
        result = result.replace(Regex(" {2,}"), " ")

        // Clean up multiple newlines
        result = result.replace(Regex("\n{3,}"), "\n\n")

        return result.trim()
    }
    
    /**
     * Removes JSON-like patterns that may have leaked through LLM response parsing.
     * This is a safety net for malformed responses.
     */
    private fun stripJsonResidues(text: String): String {
        var result = text
        
        // If text starts with { it's likely a JSON leak
        if (result.trimStart().startsWith("{")) {
            // Try to extract the reply content
            val replyMatch = Regex(""""reply"\s*:\s*"((?:[^"\\]|\\.)*)""").find(result)
            if (replyMatch != null) {
                result = replyMatch.groupValues[1]
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")
            } else {
                // Remove JSON wrapper if no reply found
                result = result
                    .removePrefix("{")
                    .removeSuffix("}")
                    .replace(Regex(""""[a-zA-Z_]+"\s*:\s*"""), "")
            }
        }
        
        // Remove any remaining JSON key patterns
        result = result.replace(Regex(""""reply"\s*:\s*"""), "")
        result = result.replace(Regex(""""text"\s*:\s*"""), "")
        result = result.replace(Regex(""""emotion"\s*:\s*"[^"]*"[,}]?\s*"""), "")
        
        // Clean up residual JSON quotes at start/end
        if (result.startsWith("\"") && !result.startsWith("\"\"")) {
            result = result.removePrefix("\"")
        }
        if (result.endsWith("\"") && !result.endsWith("\"\"")) {
            result = result.removeSuffix("\"")
        }
        if (result.endsWith("\"}")) {
            result = result.removeSuffix("\"}")
        }
        
        return result
    }
}
