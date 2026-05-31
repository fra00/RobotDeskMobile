package com.example.mydeskrobot.reasoning

import android.util.Log
import com.example.mydeskrobot.reasoning.model.ChainStatus
import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

private const val TAG = "LlmResponseParser"

/**
 * Parsed LLM response with action support.
 * Platform-agnostic: no Android dependencies.
 */
data class ParsedLlmResponse(
    val text: String,
    val emotion: String? = null,
    val action: LlmAction = LlmAction.None,
    val speakConfidence: Double? = null,
)

/**
 * JSON schema for LLM responses with tool actions.
 */
internal data class LlmResponseJson(
    val reply: String? = null,
    val text: String? = null,
    val emotion: String? = null,
    @Json(name = "imageRequired")
    val imageRequired: Boolean? = null,
    val action: ActionJson? = null,
    @Json(name = "speak_confidence")
    val speakConfidence: Double? = null,
) {
    fun spokenText(): String = reply?.trim().orEmpty().ifBlank { text?.trim().orEmpty() }
}

internal data class ActionJson(
    val type: String? = null,
    val tools: List<ToolJson>? = null,
    @Json(name = "chain_status")
    val chainStatus: String? = null,
    val parallel: Boolean? = null,
    val confirmPrompt: String? = null,
)

internal data class ToolJson(
    val name: String? = null,
    val params: Map<String, Any?>? = null,
    @Json(name = "await_result")
    val awaitResult: Boolean? = null,
    val purpose: String? = null,
)

/**
 * Parses raw LLM responses into structured [ParsedLlmResponse].
 * Handles both new action-based schema and legacy imageRequired.
 * 
 * Platform-agnostic: no Android dependencies.
 */
class LlmResponseParser(
    moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build(),
) {
    private val adapter = moshi.adapter(LlmResponseJson::class.java)
    
    fun parse(raw: String): ParsedLlmResponse {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Empty LLM response")
        }
        
        val jsonPayload = extractJsonPayload(trimmed)
        if (jsonPayload != null) {
            val parseResult = runCatching { adapter.fromJson(jsonPayload) }
            parseResult.getOrNull()?.let { json ->
                return ParsedLlmResponse(
                    text = json.spokenText(),
                    emotion = json.emotion?.trim()?.lowercase(),
                    action = parseAction(json),
                    speakConfidence = json.speakConfidence?.coerceIn(0.0, 1.0),
                )
            }
            // JSON extraction found something but Moshi parsing failed
            Log.w(TAG, "JSON parsing failed: ${parseResult.exceptionOrNull()?.message}")
            Log.d(TAG, "Raw response (first 200 chars): ${trimmed.take(200)}")
        }
        
        // Fallback: try to extract "reply" field from malformed JSON
        val replyFromMalformed = extractReplyFromMalformedJson(trimmed)
        val fallbackText = if (replyFromMalformed != null) {
            Log.d(TAG, "Fallback: extracted reply from malformed JSON")
            replyFromMalformed
        } else {
            Log.d(TAG, "Fallback: sanitizing JSON residues from raw text")
            sanitizeJsonResidues(trimmed)
        }
        
        return ParsedLlmResponse(text = fallbackText, emotion = null, action = LlmAction.None)
    }
    
    /**
     * Tries to extract the "reply" field from malformed/incomplete JSON using regex.
     * Returns null if no reply field found.
     */
    private fun extractReplyFromMalformedJson(raw: String): String? {
        // Match "reply": "..." or "reply":"..."
        val replyPattern = Regex(""""reply"\s*:\s*"((?:[^"\\]|\\.)*)""")
        val match = replyPattern.find(raw)
        val extracted = match?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\t", "\t")
            ?.replace("\\\\", "\\")
            ?.trim()
        
        return extracted?.takeIf { it.isNotBlank() }
    }
    
    /**
     * Removes JSON-like patterns from text that should be spoken.
     * Used as last resort when JSON parsing completely fails.
     */
    private fun sanitizeJsonResidues(text: String): String {
        var result = text
        
        // Remove JSON object patterns at start
        if (result.startsWith("{")) {
            // Try to find where actual text content might start
            val replyContent = extractReplyFromMalformedJson(result)
            if (replyContent != null) {
                return replyContent
            }
        }
        
        // Remove leading/trailing braces if they look like failed JSON
        result = result.removePrefix("{").removeSuffix("}")
        
        // Remove common JSON key patterns
        result = result.replace(Regex(""""reply"\s*:\s*"""), "")
        result = result.replace(Regex(""""text"\s*:\s*"""), "")
        result = result.replace(Regex(""""emotion"\s*:\s*"[^"]*",?\s*"""), "")
        result = result.replace(Regex(""""action"\s*:\s*\{[^}]*\},?\s*"""), "")
        
        // Clean up residual quotes and commas
        result = result.trim('"', ',', ' ')
        
        return result.trim()
    }
    
    private fun parseAction(json: LlmResponseJson): LlmAction {
        if (json.imageRequired == true) {
            return LlmAction.ToolCall(
                tools = listOf(
                    ToolInvocation(
                        name = "take_photo",
                        params = emptyMap(),
                        awaitResult = true,
                        purpose = "capture_image_for_analysis",
                    )
                ),
                chainStatus = ChainStatus.IN_PROGRESS,
            )
        }
        
        val actionJson = json.action ?: return LlmAction.None
        
        return when (actionJson.type?.lowercase()) {
            "none", null -> LlmAction.None
            
            "tool_call" -> {
                val tools = actionJson.tools?.mapNotNull { toolJson ->
                    toolJson.name?.let { name ->
                        ToolInvocation(
                            name = name,
                            params = toolJson.params ?: emptyMap(),
                            awaitResult = toolJson.awaitResult ?: true,
                            purpose = toolJson.purpose,
                        )
                    }
                } ?: emptyList()
                
                if (tools.isEmpty()) {
                    LlmAction.None
                } else {
                    LlmAction.ToolCall(
                        tools = tools,
                        chainStatus = parseChainStatus(actionJson.chainStatus),
                        parallel = actionJson.parallel ?: false,
                    )
                }
            }
            
            "confirm_required" -> {
                val tool = actionJson.tools?.firstOrNull()?.let { toolJson ->
                    toolJson.name?.let { name ->
                        ToolInvocation(
                            name = name,
                            params = toolJson.params ?: emptyMap(),
                            awaitResult = toolJson.awaitResult ?: true,
                            purpose = toolJson.purpose,
                        )
                    }
                }
                
                if (tool != null && actionJson.confirmPrompt != null) {
                    LlmAction.ConfirmRequired(
                        tool = tool,
                        confirmPrompt = actionJson.confirmPrompt,
                    )
                } else {
                    LlmAction.None
                }
            }
            
            else -> LlmAction.None
        }
    }
    
    private fun parseChainStatus(status: String?): ChainStatus {
        return when (status?.lowercase()) {
            "complete" -> ChainStatus.COMPLETE
            "failed" -> ChainStatus.FAILED
            else -> ChainStatus.IN_PROGRESS
        }
    }
    
    private fun extractJsonPayload(raw: String): String? {
        val fence = Regex("""```(?:json)?\s*([\s\S]*?)```""", RegexOption.IGNORE_CASE)
        fence.find(raw)?.groupValues?.getOrNull(1)?.trim()?.let { 
            if (it.startsWith("{")) return it 
        }
        
        if (raw.startsWith("{") && raw.endsWith("}")) return raw
        
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1)
        }
        return null
    }
}
