package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.domain.time.RelativeDateNormalizer
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class SaveMemoryTool(
    private val memoryRepository: UserMemoryRepository,
) : Tool {

    constructor(context: Context) : this(UserMemoryRepository.create(context))

    override val name: String = "save_memory"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Save a durable fact about the user (name, preferences, routines).",
            parameters = listOf(
                ToolParameter(
                    name = "value",
                    type = "string",
                    description = "Short normalized fact in Italian; resolve oggi/domani/weekdays to absolute dates (e.g. \"il 3 giugno 2026\")",
                    required = true,
                ),
                ToolParameter(
                    name = "category",
                    type = "string",
                    description = "IDENTITY | PREFERENCE | ROUTINE | FACT (default FACT)",
                    required = false,
                ),
                ToolParameter(
                    name = "confidence",
                    type = "number",
                    description = "0.0–1.0 confidence (default 0.85)",
                    required = false,
                ),
            ),
            returns = "memory_id (integer), category, value",
            example = """{"name": "save_memory", "params": {"value": "L'utente si chiama Francesco", "category": "IDENTITY"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val rawValue = (invocation.params["value"] as? String)?.trim().orEmpty()
        if (rawValue.isBlank()) {
            return ToolResult.Error(
                message = "Parametro 'value' mancante o vuoto",
                code = "MISSING_PARAM",
            )
        }

        val value = RelativeDateNormalizer.normalize(rawValue)

        val category = MemoryToolSupport.parseCategory(invocation.params["category"])
            ?: MemoryCategory.FACT
        val confidence = MemoryToolSupport.parseConfidence(invocation.params["confidence"])

        val id = memoryRepository.upsert(
            category = category,
            value = value,
            confidence = confidence,
            sourceMessageId = MemoryToolSupport.SOURCE_MESSAGE_LLM_TOOL,
        )
        if (id < 0L) {
            return ToolResult.Error(message = "Impossibile salvare la memoria", code = "SAVE_FAILED")
        }

        memoryRepository.pruneIfNeeded(DEFAULT_MAX_ITEMS)

        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "memory_id" to id,
                "category" to category.name,
                "value" to value,
            ),
        )
    }

    companion object {
        private const val DEFAULT_MAX_ITEMS = 300
    }
}
