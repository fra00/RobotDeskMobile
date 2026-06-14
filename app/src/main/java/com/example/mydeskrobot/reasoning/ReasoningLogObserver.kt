package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult

/**
 * Optional sink for LLM reasoning trace (think, tools, outcomes). Layer 1 implements for debug UI.
 */
interface ReasoningLogObserver {
    fun onUserInput(text: String)
    fun onSystemInput(formattedText: String)
    fun onLlmStep(
        step: Int,
        think: String?,
        reply: String?,
        emotion: String?,
        action: LlmAction,
        chainStatusLabel: String?,
    )
    fun onToolResult(tool: ToolInvocation, result: ToolResult)
    fun onOutcome(message: String)
}

object NoOpReasoningLogObserver : ReasoningLogObserver {
    override fun onUserInput(text: String) = Unit
    override fun onSystemInput(formattedText: String) = Unit
    override fun onLlmStep(
        step: Int,
        think: String?,
        reply: String?,
        emotion: String?,
        action: LlmAction,
        chainStatusLabel: String?,
    ) = Unit
    override fun onToolResult(tool: ToolInvocation, result: ToolResult) = Unit
    override fun onOutcome(message: String) = Unit
}
