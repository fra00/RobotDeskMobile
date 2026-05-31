package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class ListItemsTool(
    private val repository: ListItemRepository,
) : Tool {

    constructor(context: Context) : this(ListItemRepository.create(context))

    override val name: String = "list_items"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "List structured items (notes, todos, shopping). Filter by type, checked state, or search text.",
            parameters = listOf(
                ToolParameter(
                    name = "type",
                    type = "string",
                    description = "Optional: NOTE | TODO | SHOPPING",
                    required = false,
                ),
                ToolParameter(
                    name = "checked",
                    type = "boolean",
                    description = "Optional: true = done/bought only, false = pending only",
                    required = false,
                ),
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Optional substring search in item text",
                    required = false,
                ),
                ToolParameter(
                    name = "limit",
                    type = "integer",
                    description = "Max items (default 30, max 100)",
                    required = false,
                ),
            ),
            returns = "count, items (array of id, type, text, checked)",
            example = """{"name": "list_items", "params": {"type": "SHOPPING"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val type = ListToolSupport.parseType(invocation.params["type"])
        val checked = ListToolSupport.parseChecked(invocation.params["checked"])
        val query = (invocation.params["query"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val limit = ListToolSupport.parseLimit(invocation.params["limit"])

        val items = repository.list(
            type = type,
            checked = checked,
            query = query,
            limit = limit,
        )

        val mapped = items.map { ListToolSupport.entityToMap(it) }
        return ToolResult.Success(
            data = mapOf(
                "count" to mapped.size,
                "items" to mapped,
            ),
        )
    }
}
