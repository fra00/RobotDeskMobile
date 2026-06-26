package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class ListMemoriesTool private constructor(
    private val unifiedMemoryRepository: UnifiedMemoryRepository?,
    private val legacyTestRepository: UserMemoryRepository?,
) : Tool {

    constructor(unifiedMemoryRepository: UnifiedMemoryRepository) : this(unifiedMemoryRepository, null)

    constructor(legacyMemoryRepository: UserMemoryRepository) : this(null, legacyMemoryRepository)

    constructor(context: Context) : this(
        com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory.createRepository(context),
        null,
    )

    override val name: String = "list_memories"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "List stored memories (optionally filter by category or search text). Default lists user-facing memories only; use category OBSERVATION/INTENT/PATTERN for autonomy. Use query for topic search (e.g. cane, INTENT pranzo).",
            parameters = listOf(
                ToolParameter(
                    name = "category",
                    type = "string",
                    description = "Optional: IDENTITY | PREFERENCE | ROUTINE | FACT | OBSERVATION | INTENT | PATTERN",
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

        val memories = if (unifiedMemoryRepository != null) {
            val items = when {
                query.isNotBlank() -> unifiedMemoryRepository.searchToolRelevant(
                    query = query,
                    limit = limit,
                    includeRobotInternal = true,
                )
                category != null -> unifiedMemoryRepository.getToolByCategory(category, limit)
                else -> unifiedMemoryRepository.getUserFacingActiveDocuments().take(limit)
            }
            items.map { MemoryToolSupport.documentToMap(it) }
        } else {
            val legacy = legacyTestRepository
                ?: return ToolResult.Error(message = "Memoria non disponibile", code = "NOT_FOUND")
            val items = when {
                query.isNotBlank() -> legacy.searchRelevant(query, limit, includeRobotInternal = true)
                category != null -> legacy.getByCategory(category, limit)
                else -> legacy.getUserFacingActive().take(limit)
            }
            items.map { MemoryToolSupport.legacyEntityToMap(it) }
        }

        return ToolResult.Success(
            data = mapOf(
                "count" to memories.size,
                "memories" to memories,
            ),
        )
    }
}
