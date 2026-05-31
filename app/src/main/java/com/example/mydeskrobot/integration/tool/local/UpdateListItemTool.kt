package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class UpdateListItemTool(
    private val repository: ListItemRepository,
) : Tool {

    constructor(context: Context) : this(ListItemRepository.create(context))

    override val name: String = "update_list_item"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Update a list item by id (from list_items). Change text and/or checked state.",
            parameters = listOf(
                ToolParameter(
                    name = "item_id",
                    type = "integer",
                    description = "Item id to update (required)",
                    required = true,
                ),
                ToolParameter(
                    name = "text",
                    type = "string",
                    description = "New item text (optional)",
                    required = false,
                ),
                ToolParameter(
                    name = "checked",
                    type = "boolean",
                    description = "Mark done/bought (true) or pending (false)",
                    required = false,
                ),
            ),
            returns = "success, item_id, type, text, checked",
            example = """{"name": "update_list_item", "params": {"item_id": 3, "checked": true}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val itemId = (invocation.params["item_id"] as? Number)?.toLong()
            ?: (invocation.params["id"] as? Number)?.toLong()

        if (itemId == null) {
            return ToolResult.Error(
                message = "Parametro 'item_id' mancante",
                code = "MISSING_PARAM",
            )
        }

        val text = invocation.params["text"] as? String
        val checked = ListToolSupport.parseChecked(invocation.params["checked"])

        if (text == null && checked == null) {
            return ToolResult.Error(
                message = "Serve almeno 'text' o 'checked' da aggiornare",
                code = "MISSING_PARAM",
            )
        }

        val updated = repository.update(itemId, text = text, checked = checked)
        if (!updated) {
            return ToolResult.Error(
                message = "Elemento $itemId non trovato",
                code = "NOT_FOUND",
                recoverable = true,
            )
        }

        val entity = repository.getById(itemId)
            ?: return ToolResult.Error(message = "Elemento $itemId non trovato", code = "NOT_FOUND")

        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "item_id" to entity.id,
                "type" to entity.type.name.lowercase(),
                "text" to entity.text,
                "checked" to entity.checked,
            ),
        )
    }
}
