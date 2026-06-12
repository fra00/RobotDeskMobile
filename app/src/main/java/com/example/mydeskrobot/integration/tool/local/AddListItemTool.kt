package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.domain.time.RelativeDateNormalizer
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class AddListItemTool(
    private val repository: ListItemRepository,
) : Tool {

    constructor(context: Context) : this(ListItemRepository.create(context))

    override val name: String = "add_list_item"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Add an item to a structured list (notes, todos, or shopping list).",
            parameters = listOf(
                ToolParameter(
                    name = "type",
                    type = "string",
                    description = "NOTE | TODO | SHOPPING (required)",
                    required = true,
                ),
                ToolParameter(
                    name = "text",
                    type = "string",
                    description = "Item text in Italian; resolve oggi/domani/weekdays to absolute dates (e.g. \"il 3 giugno 2026\")",
                    required = true,
                ),
                ToolParameter(
                    name = "checked",
                    type = "boolean",
                    description = "For TODO/SHOPPING: true if already done/bought (default false)",
                    required = false,
                ),
            ),
            returns = "success, item_id, type, text, checked",
            example = """{"name": "add_list_item", "params": {"type": "SHOPPING", "text": "latte"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val type = ListToolSupport.parseType(invocation.params["type"])
            ?: return ToolResult.Error(
                message = "Parametro 'type' mancante o non valido (NOTE | TODO | SHOPPING)",
                code = "MISSING_PARAM",
            )

        val rawText = (invocation.params["text"] as? String)?.trim().orEmpty()
        if (rawText.isBlank()) {
            return ToolResult.Error(
                message = "Parametro 'text' mancante o vuoto",
                code = "MISSING_PARAM",
            )
        }

        val text = RelativeDateNormalizer.normalize(rawText)
        val checked = ListToolSupport.parseChecked(invocation.params["checked"]) ?: false

        val id = repository.add(type, text, checked)
        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "item_id" to id,
                "type" to type.name.lowercase(),
                "text" to text,
                "checked" to checked,
            ),
        )
    }
}
