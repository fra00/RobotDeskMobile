package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.integration.telephony.PhoneContactResolveResult
import com.example.mydeskrobot.integration.telephony.PhoneContactResolver
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Resolves a contact name to a phone number from rubrica and stored memories.
 */
class ResolvePhoneContactTool(
    private val resolver: PhoneContactResolver,
) : Tool {

    override val name: String = "resolve_phone_contact"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Find a phone number for a contact name (rubrica + memories). " +
                "Use before dial_phone when user says chiama/telefona [nome] without a number.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Contact name as spoken (e.g. Marco, mamma, madre)",
                    required = true,
                ),
            ),
            returns = "found, display_name, number, source, ambiguous, matches",
            example = """{"name": "resolve_phone_contact", "params": {"query": "mamma"}, "await_result": true}""",
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

        return when (val result = resolver.resolve(query)) {
            is PhoneContactResolveResult.InvalidQuery -> ToolResult.Error(
                message = "Nome contatto non valido",
                code = "INVALID_QUERY",
            )

            is PhoneContactResolveResult.PermissionDenied -> ToolResult.Success(
                data = mapOf(
                    "found" to false,
                    "query" to query,
                    "contacts_permission" to false,
                    "hint" to "Permesso rubrica non concesso — chiedi il numero o concedi accesso contatti",
                ),
            )

            is PhoneContactResolveResult.NotFound -> ToolResult.Success(
                data = mapOf(
                    "found" to false,
                    "query" to query,
                    "hint" to "Nessun contatto o memoria con numero per questo nome",
                ),
            )

            is PhoneContactResolveResult.Single -> ToolResult.Success(
                data = matchPayload(result.match, ambiguous = false),
            )

            is PhoneContactResolveResult.Multiple -> ToolResult.Success(
                data = mapOf(
                    "found" to true,
                    "ambiguous" to true,
                    "query" to query,
                    "matches" to result.matches.map { matchPayload(it, ambiguous = null) },
                    "hint" to "Più contatti trovati — chiedi quale intendeva l'utente",
                ),
            )
        }
    }

    private fun matchPayload(
        match: com.example.mydeskrobot.integration.telephony.ContactPhoneMatch,
        ambiguous: Boolean?,
    ): Map<String, Any?> = mapOf(
        "found" to true,
        "ambiguous" to ambiguous,
        "display_name" to match.displayName,
        "number" to match.number,
        "source" to match.source.name.lowercase(),
        "score" to match.score,
    )
}
