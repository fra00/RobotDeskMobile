package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.reasoning.ReasoningLogObserver
import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ring buffer of formatted reasoning log lines for the debug UI.
 */
class ReasoningLogBuffer(
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxTotalChars: Int = MAX_TOTAL_CHARS,
    private val maxEntryChars: Int = MAX_ENTRY_CHARS,
) : ReasoningLogObserver {

    private val entries = ArrayDeque<String>(maxEntries)
    private var totalChars = 0

    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    override fun onUserInput(text: String) {
        appendSection("UTENTE", text)
    }

    override fun onSystemInput(formattedText: String) {
        appendSection("INPUT SISTEMA", formattedText)
    }

    override fun onLlmStep(
        step: Int,
        think: String?,
        reply: String?,
        emotion: String?,
        action: LlmAction,
        chainStatusLabel: String?,
    ) {
        val body = buildString {
            appendLine("── Step $step ──")
            think?.takeIf { it.isNotBlank() }?.let {
                appendLine("think:")
                appendLine(truncate(it))
            }
            reply?.takeIf { it.isNotBlank() }?.let {
                appendLine("reply: $it")
            }
            emotion?.let { appendLine("emotion: $it") }
            chainStatusLabel?.let { appendLine("chain: $it") }
            append(formatAction(action))
        }.trimEnd()
        appendSection("LLM", body)
    }

    override fun onToolResult(tool: ToolInvocation, result: ToolResult) {
        val body = buildString {
            appendLine("tool: ${tool.name}")
            appendLine("mode: ${describeToolMode(tool)}")
            tool.purpose?.takeIf { it.isNotBlank() }?.let {
                appendLine("purpose: $it")
            }
            if (tool.params.isNotEmpty()) {
                appendLine("params: ${formatParams(tool.params)}")
            }
            appendLine("result: ${summarizeToolResult(result)}")
        }.trimEnd()
        appendSection("TOOL", body)
    }

    override fun onOutcome(message: String) {
        appendSection("ESITO", message)
    }

    fun clear() {
        entries.clear()
        totalChars = 0
        publish()
    }

    private fun appendSection(kind: String, body: String) {
        if (body.isBlank()) return
        val timestamp = timeFormat.format(Date())
        val truncatedBody = truncate(body)
        val block = "[$timestamp] $kind\n$truncatedBody"
        val blockLen = block.length

        while (entries.isNotEmpty() && (entries.size >= maxEntries || totalChars + blockLen > maxTotalChars)) {
            totalChars -= entries.removeFirst().length + 1
        }

        entries.addLast(block)
        totalChars += blockLen + 1
        publish()
    }

    private fun publish() {
        _displayText.value = entries.joinToString("\n\n")
    }

    private fun truncate(text: String): String =
        if (text.length <= maxEntryChars) text else text.take(maxEntryChars) + "…"

    companion object {
        const val MAX_ENTRIES = 120
        const val MAX_TOTAL_CHARS = 200_000
        const val MAX_ENTRY_CHARS = 4_000

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.ITALY)

        fun formatAction(action: LlmAction): String = when (action) {
            is LlmAction.None -> "action: none"
            is LlmAction.ConfirmRequired -> buildString {
                appendLine("action: confirm_required")
                appendLine("confirm: ${action.confirmPrompt}")
                append(describeToolInvocation(action.tool))
            }
            is LlmAction.ToolCall -> buildString {
                appendLine("action: tool_call")
                action.tools.forEach { tool ->
                    appendLine(describeToolInvocation(tool))
                }
            }
        }

        fun describeToolInvocation(tool: ToolInvocation): String = buildString {
            append("• ${tool.name} [${describeToolMode(tool)}]")
            tool.purpose?.takeIf { it.isNotBlank() }?.let { purpose ->
                append(" purpose=\"$purpose\"")
            }
            if (tool.params.isNotEmpty()) {
                append(" {${formatParams(tool.params)}}")
            }
        }

        fun describeToolMode(tool: ToolInvocation): String {
            val fireAndCheck = tool.params["fire_and_check"] == true ||
                tool.params["fire_and_check"] == "true"
            return when {
                fireAndCheck -> "fire-and-check"
                !tool.awaitResult -> "fire-and-forget"
                else -> "await"
            }
        }

        fun formatParams(params: Map<String, Any?>): String =
            params.entries.joinToString(", ") { (key, value) ->
                "$key=${value?.toString().orEmpty()}"
            }

        fun summarizeToolResult(result: ToolResult): String = when (result) {
            is ToolResult.Success -> "OK ${result.data}"
            is ToolResult.Error -> "ERR ${result.code}: ${result.message}"
            is ToolResult.NeedsConfirmation -> "CONFIRM ${result.prompt}"
            is ToolResult.BinaryData -> "BINARY ${result.mimeType} (${result.data.size} bytes)"
        }
    }
}
