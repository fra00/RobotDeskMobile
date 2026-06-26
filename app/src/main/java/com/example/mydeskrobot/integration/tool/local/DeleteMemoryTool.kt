package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class DeleteMemoryTool private constructor(
    private val unifiedMemoryRepository: UnifiedMemoryRepository?,
    private val legacyTestRepository: UserMemoryRepository?,
) : Tool {

    constructor(unifiedMemoryRepository: UnifiedMemoryRepository) : this(unifiedMemoryRepository, null)

    constructor(legacyMemoryRepository: UserMemoryRepository) : this(null, legacyMemoryRepository)

    constructor(context: Context) : this(
        com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory.createRepository(context),
        null,
    )

    override val name: String = "delete_memory"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Forget stored memories by id or by topic (natural language; fuzzy match, not exact text).",
            parameters = listOf(
                ToolParameter(
                    name = "memory_id",
                    type = "integer",
                    description = "Single memory id to delete (from list_memories)",
                    required = false,
                ),
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Topic keywords from the user request (e.g. \"cane Brina\"); deletes all related memories",
                    required = false,
                ),
            ),
            returns = "success, deleted_count, deleted_memories (text snippets)",
            example = """{"name": "delete_memory", "params": {"query": "cane Brina"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val unified = unifiedMemoryRepository
        val legacy = legacyTestRepository

        val memoryId = (invocation.params["memory_id"] as? Number)?.toLong()
            ?: (invocation.params["id"] as? Number)?.toLong()

        if (memoryId != null) {
            val deleted = when {
                unified != null -> unified.deleteById(memoryId)
                legacy != null -> legacy.deleteById(memoryId)
                else -> false
            }
            if (!deleted) {
                return ToolResult.Error(
                    message = "Memoria $memoryId non trovata",
                    code = "NOT_FOUND",
                    recoverable = true,
                )
            }
            return ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "deleted_count" to 1,
                    "memory_id" to memoryId,
                ),
            )
        }

        val query = (invocation.params["query"] as? String)?.trim().orEmpty()
        if (query.isBlank()) {
            return ToolResult.Error(
                message = "Serve 'memory_id' oppure 'query'",
                code = "MISSING_PARAM",
            )
        }

        val result = when {
            unified != null -> unified.forgetByTopic(query)
            legacy != null -> legacy.forgetByTopic(query)
            else -> return ToolResult.Error(message = "Memoria non disponibile", code = "NOT_FOUND")
        }
        if (result.deletedCount == 0) {
            return ToolResult.Error(
                message = "Nessuna memoria trovata per \"$query\"",
                code = "NOT_FOUND",
                recoverable = true,
            )
        }

        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "deleted_count" to result.deletedCount,
                "query" to query,
                "deleted_memories" to result.deletedValues,
            ),
        )
    }
}
