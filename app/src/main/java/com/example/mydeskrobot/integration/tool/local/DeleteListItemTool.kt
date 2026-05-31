package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class DeleteListItemTool(
    private val repository: ListItemRepository,
) : Tool {

    constructor(context: Context) : this(ListItemRepository.create(context))

    override val name: String = "delete_list_item"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Delete a list item by id or by matching text.",
            parameters = listOf(
                ToolParameter(
                    name = "item_id",
                    type = "integer",
                    description = "Item id to delete (from list_items)",
                    required = false,
                ),
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Substring match on item text (if item_id omitted)",
                    required = false,
                ),
                ToolParameter(
                    name = "type",
                    type = "string",
                    description = "Optional: NOTE | TODO | SHOPPING (with query)",
                    required = false,
                ),
            ),
            returns = "success, deleted_count",
            example = """{"name": "delete_list_item", "params": {"query": "latte", "type": "SHOPPING"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val itemId = (invocation.params["item_id"] as? Number)?.toLong()
            ?: (invocation.params["id"] as? Number)?.toLong()

        if (itemId != null) {
            val deleted = repository.deleteById(itemId)
            if (!deleted) {
                return ToolResult.Error(
                    message = "Elemento $itemId non trovato",
                    code = "NOT_FOUND",
                    recoverable = true,
                )
            }
            return ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "deleted_count" to 1,
                    "item_id" to itemId,
                ),
            )
        }

        val query = (invocation.params["query"] as? String)?.trim().orEmpty()
        if (query.isBlank()) {
            return ToolResult.Error(
                message = "Serve 'item_id' oppure 'query'",
                code = "MISSING_PARAM",
            )
        }

        val type = ListToolSupport.parseType(invocation.params["type"])
        val deletedCount = repository.deleteByTextMatch(query, type)
        if (deletedCount == 0) {
            return ToolResult.Error(
                message = "Nessun elemento trovato per \"$query\"",
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
