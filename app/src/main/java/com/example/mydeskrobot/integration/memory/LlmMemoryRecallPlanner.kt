package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlan
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlanParser
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlanner
import com.example.mydeskrobot.reasoning.memory.RecallPlanException
import com.example.mydeskrobot.reasoning.memory.RecallPlanFailure
import com.example.mydeskrobot.reasoning.model.ConversationMessage

class LlmMemoryRecallPlanner(
    private val llmClient: LlmClient,
    private val systemPrompt: String,
) : MemoryRecallPlanner {

    override suspend fun plan(
        userText: String,
        nowMillis: Long,
    ): Result<MemoryRecallPlan> {
        val normalized = userText.trim()
        if (normalized.isBlank()) {
            return Result.failure(RecallPlanException(RecallPlanFailure.ParseError("empty_user_text")))
        }
        if (!llmClient.isConfigured()) {
            return Result.failure(RecallPlanException(RecallPlanFailure.NotConfigured))
        }
        val llmResult = llmClient.chat(
            messages = listOf(ConversationMessage.User(normalized)),
            systemPrompt = systemPrompt,
        )
        llmResult.exceptionOrNull()?.let { error ->
            return Result.failure(RecallPlanException(RecallPlanFailure.LlmError(error.message ?: "llm_error")))
        }
        val raw = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (raw.isBlank()) {
            return Result.failure(RecallPlanException(RecallPlanFailure.EmptyOutput))
        }
        val parsed = MemoryRecallPlanParser.parse(raw)
        if (parsed == null) {
            return Result.failure(RecallPlanException(RecallPlanFailure.ParseError("invalid_json")))
        }
        return Result.success(parsed)
    }
}
