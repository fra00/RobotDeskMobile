package com.example.mydeskrobot.reasoning.tool

/**
 * Definition of a tool for the system prompt.
 * Platform-agnostic representation.
 */
data class ToolDefinition(
    /** Unique tool name (e.g., "get_weather") */
    val name: String,
    
    /** Human-readable description for the LLM */
    val description: String,
    
    /** Parameter definitions */
    val parameters: List<ToolParameter> = emptyList(),
    
    /** Return type description */
    val returns: String? = null,
    
    /** Example invocation for the LLM */
    val example: String? = null,
)

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val defaultValue: Any? = null,
)

/**
 * Formats tool definitions for inclusion in the system prompt.
 */
fun List<ToolDefinition>.toSystemPromptSection(): String {
    if (isEmpty()) return ""
    
    return buildString {
        appendLine("AVAILABLE TOOLS:")
        this@toSystemPromptSection.forEach { tool ->
            appendLine()
            appendLine("TOOL: ${tool.name}")
            appendLine("DESCRIPTION: ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                appendLine("PARAMS:")
                tool.parameters.forEach { param ->
                    val req = if (param.required) "required" else "optional"
                    appendLine("  - ${param.name} (${param.type}, $req): ${param.description}")
                }
            }
            tool.returns?.let { appendLine("RETURNS: $it") }
            tool.example?.let { appendLine("EXAMPLE: $it") }
        }
    }
}
