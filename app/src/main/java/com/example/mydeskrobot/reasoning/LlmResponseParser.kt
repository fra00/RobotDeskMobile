package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.ChainStatus
import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Parsed LLM response with action support.
 * Platform-agnostic: no Android dependencies.
 */
data class ParsedLlmResponse(
    val text: String,
    val emotion: String? = null,
    val action: LlmAction = LlmAction.None,
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
            runCatching { adapter.fromJson(jsonPayload) }.getOrNull()?.let { json ->
                return ParsedLlmResponse(
                    text = json.spokenText(),
                    emotion = json.emotion?.trim()?.lowercase(),
                    action = parseAction(json),
                )
            }
        }
        
        return ParsedLlmResponse(text = trimmed, emotion = null, action = LlmAction.None)
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
