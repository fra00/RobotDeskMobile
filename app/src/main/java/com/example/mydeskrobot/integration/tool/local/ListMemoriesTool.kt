package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class ListMemoriesTool(
    private val memoryRepository: UserMemoryRepository,
) : Tool {

    constructor(context: Context) : this(UserMemoryRepository.create(context))

    override val name: String = "list_memories"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "List stored user memories (optionally filter by category or search text). Use query for topic search (e.g. cane, lavoro). After result, answer using value fields from memories, not count alone.",
            parameters = listOf(
                ToolParameter(
                    name = "category",
                    type = "string",
                    description = "Optional: IDENTITY | PREFERENCE | ROUTINE | FACT",
                    required = false,
                ),
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Optional topic/search text (fuzzy match, not exact wording)",
                    required = false,
                ),
                ToolParameter(
                    name = "limit",
                    type = "integer",
                    description = "Max items (default 20, max 50)",
                    required = false,
                ),
            ),
            returns = "count, memories (array of id, category, value, confidence)",
            example = """{"name": "list_memories", "params": {}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val limit = MemoryToolSupport.parseLimit(invocation.params["limit"])
        val category = MemoryToolSupport.parseCategory(invocation.params["category"])
        val query = (invocation.params["query"] as? String)?.trim().orEmpty()

        val items = when {
            query.isNotBlank() -> memoryRepository.searchRelevant(query, limit)
            category != null -> memoryRepository.getByCategory(category, limit)
            else -> memoryRepository.getAllActive().take(limit)
        }

        val memories = items.map { MemoryToolSupport.entityToMap(it) }
        return ToolResult.Success(
            data = mapOf(
                "count" to memories.size,
                "memories" to memories,
            ),
        )
    }
}
