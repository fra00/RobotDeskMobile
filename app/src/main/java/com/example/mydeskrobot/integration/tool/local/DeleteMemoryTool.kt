package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class DeleteMemoryTool(
    private val memoryRepository: UserMemoryRepository,
) : Tool {

    constructor(context: Context) : this(UserMemoryRepository.create(context))

    override val name: String = "delete_memory"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Forget a stored memory by id (from list_memories) or by matching text.",
            parameters = listOf(
                ToolParameter(
                    name = "memory_id",
                    type = "integer",
                    description = "Memory id to delete",
                    required = false,
                ),
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Substring match on memory value (if memory_id omitted)",
                    required = false,
                ),
            ),
            returns = "success (boolean), deleted_count (integer)",
            example = """{"name": "delete_memory", "params": {"query": "Francesco"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val memoryId = (invocation.params["memory_id"] as? Number)?.toLong()
            ?: (invocation.params["id"] as? Number)?.toLong()

        if (memoryId != null) {
            val deleted = memoryRepository.deleteById(memoryId)
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

        val deletedCount = memoryRepository.forgetByText(query)
        if (deletedCount == 0) {
            return ToolResult.Error(
                message = "Nessuna memoria trovata per \"$query\"",
                code = "NOT_FOUND",
                recoverable = true,
            )
        }

        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "deleted_count" to deletedCount,
                "query" to query,
            ),
        )
    }
}
