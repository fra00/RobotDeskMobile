package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.integration.whatsapp.WhatsAppChatType
import com.example.mydeskrobot.integration.whatsapp.WhatsAppTargetMatch
import com.example.mydeskrobot.integration.whatsapp.WhatsAppTargetResolveResult
import com.example.mydeskrobot.integration.whatsapp.WhatsAppTargetResolver
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class ResolveWhatsAppTargetTool(
    private val resolver: WhatsAppTargetResolver,
) : Tool {

    override val name: String = "resolve_whatsapp_target"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Find WhatsApp chat target (contact or group) by name in contacts/memories. " +
                "Use before send_whatsapp when user names a person or group without a number.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Contact or group name (e.g. Marco, siamo i migliori, mamma)",
                    required = true,
                ),
                ToolParameter(
                    name = "chat_type",
                    type = "string",
                    description = "group | contact | any (default any). Use group when user says gruppo/chat di gruppo.",
                    required = false,
                ),
            ),
            returns = "found, display_name, send_id, chat_type, source, ambiguous, matches",
            example = """{"name": "resolve_whatsapp_target", "params": {"query": "siamo i migliori", "chat_type": "group"}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val query = invocation.params["query"]?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            return ToolResult.Error(
                message = "Parametro 'query' mancante",
                code = "MISSING_PARAM",
            )
        }

        val preferGroup = parseChatType(invocation.params["chat_type"]) == WhatsAppChatType.GROUP

        return when (val result = resolver.resolve(query, preferGroup)) {
            WhatsAppTargetResolveResult.InvalidQuery -> ToolResult.Error(
                message = "Nome chat non valido",
                code = "INVALID_QUERY",
            )

            WhatsAppTargetResolveResult.PermissionDenied -> ToolResult.Success(
                data = mapOf(
                    "found" to false,
                    "query" to query,
                    "contacts_permission" to false,
                    "hint" to "Permesso rubrica non concesso — salva il gruppo in memoria con JID o concedi accesso contatti",
                ),
            )

            is WhatsAppTargetResolveResult.NotFound -> ToolResult.Success(
                data = mapOf(
                    "found" to false,
                    "query" to query,
                    "hint" to "Nessuna chat WhatsApp trovata per questo nome",
                ),
            )

            is WhatsAppTargetResolveResult.Single -> ToolResult.Success(
                data = matchPayload(result.match, ambiguous = false),
            )

            is WhatsAppTargetResolveResult.Multiple -> ToolResult.Success(
                data = mapOf(
                    "found" to true,
                    "ambiguous" to true,
                    "query" to query,
                    "matches" to result.matches.map { matchPayload(it, ambiguous = null) },
                    "hint" to "Più chat trovate — chiedi quale intendeva l'utente",
                ),
            )
        }
    }

    private fun parseChatType(raw: Any?): WhatsAppChatType? =
        when (raw?.toString()?.trim()?.lowercase()) {
            "group", "gruppo" -> WhatsAppChatType.GROUP
            "contact", "contatto" -> WhatsAppChatType.CONTACT
            "any", null, "" -> null
            else -> null
        }

    private fun matchPayload(match: WhatsAppTargetMatch, ambiguous: Boolean?): Map<String, Any?> =
        mapOf(
            "found" to true,
            "ambiguous" to ambiguous,
            "display_name" to match.displayName,
            "send_id" to match.sendId,
            "chat_type" to match.chatType.name.lowercase(),
            "source" to match.source.name.lowercase(),
            "score" to match.score,
        )
}
