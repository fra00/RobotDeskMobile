package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.domain.time.RelativeDateNormalizer
import com.example.mydeskrobot.memory.AutonomyUpsertResult
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

class SaveMemoryTool private constructor(
    private val unifiedMemoryRepository: UnifiedMemoryRepository?,
    private val legacyTestRepository: UserMemoryRepository?,
) : Tool {

    constructor(unifiedMemoryRepository: UnifiedMemoryRepository) : this(unifiedMemoryRepository, null)

    /** Legacy-only path for unit tests. */
    constructor(legacyMemoryRepository: UserMemoryRepository) : this(null, legacyMemoryRepository)

    constructor(context: Context) : this(
        com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory.createRepository(context),
        null,
    )

    override val name: String = "save_memory"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Save a fact: durable user knowledge (IDENTITY/PREFERENCE/ROUTINE/FACT) or robot-internal autonomy notes (OBSERVATION/INTENT/PATTERN with ttl_days).",
            parameters = listOf(
                ToolParameter(
                    name = "value",
                    type = "string",
                    description = "Short normalized statement in Italian; resolve oggi/domani/weekdays to absolute dates (e.g. \"il 3 giugno 2026\")",
                    required = true,
                ),
                ToolParameter(
                    name = "category",
                    type = "string",
                    description = "IDENTITY | PREFERENCE | ROUTINE | FACT | OBSERVATION | INTENT | PATTERN (default FACT)",
                    required = false,
                ),
                ToolParameter(
                    name = "confidence",
                    type = "number",
                    description = "0.0–1.0 confidence (default 0.85)",
                    required = false,
                ),
                ToolParameter(
                    name = "ttl_days",
                    type = "integer",
                    description = "Optional TTL in days for OBSERVATION (default 7), INTENT (default 1), PATTERN (default 30). Ignored for user-facing categories.",
                    required = false,
                ),
                ToolParameter(
                    name = "pinned",
                    type = "boolean",
                    description = "If true, fact is never pruned (user name, allergies, emergencies, explicit \"ricordalo sempre\"). Default false.",
                    required = false,
                ),
            ),
            returns = "memory_id (integer), category, value",
            example = """{"name": "save_memory", "params": {"value": "L'utente si chiama Francesco", "category": "IDENTITY", "pinned": true}, "await_result": true}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        legacyTestRepository?.let { return executeLegacyOnly(invocation, it) }
        val unified = unifiedMemoryRepository
            ?: return ToolResult.Error(message = "Memoria non disponibile", code = "SAVE_FAILED")

        val rawValue = (invocation.params["value"] as? String)?.trim().orEmpty()
        if (rawValue.isBlank()) {
            return ToolResult.Error(
                message = "Parametro 'value' mancante o vuoto",
                code = "MISSING_PARAM",
            )
        }

        val value = RelativeDateNormalizer.normalize(rawValue)
        val category = MemoryToolSupport.parseCategory(invocation.params["category"]) ?: MemoryCategory.FACT
        val confidence = MemoryToolSupport.parseConfidence(invocation.params["confidence"])
        val ttlDays = MemoryToolSupport.parseTtlDays(invocation.params["ttl_days"])
        val pinned = MemoryToolSupport.parsePinned(invocation.params["pinned"])

        if (MemoryCategory.isUserFacing(category) && ttlDays != null) {
            return ToolResult.Error(
                message = "ttl_days applies only to OBSERVATION, INTENT, or PATTERN",
                code = "INVALID_PARAM",
            )
        }

        if (MemoryCategory.isRobotInternal(category)) {
            return when (
                val result = unified.upsertAutonomy(
                    category = category,
                    value = value,
                    confidence = confidence,
                    source = MemoryDocumentSource.TOOL,
                    ttlDays = ttlDays,
                )
            ) {
                is AutonomyUpsertResult.Success -> ToolResult.Success(
                    data = mapOf(
                        "success" to true,
                        "memory_id" to result.memoryId,
                        "category" to category.name,
                        "value" to value,
                    ),
                )
                AutonomyUpsertResult.IntentCapReached -> ToolResult.Error(
                    message = "Massimo ${UserMemoryRepository.MAX_ACTIVE_INTENTS} INTENT attivi; chiudi un INTENT prima di crearne uno nuovo",
                    code = "INTENT_CAP_REACHED",
                )
                AutonomyUpsertResult.InvalidValue -> ToolResult.Error(
                    message = "Impossibile salvare la memoria",
                    code = "SAVE_FAILED",
                )
            }
        }

        val id = unified.upsertUserFacingFact(
            category = category,
            value = value,
            confidence = confidence,
            source = MemoryDocumentSource.TOOL,
            isPinned = pinned,
        )
        if (id < 0L) {
            return ToolResult.Error(message = "Impossibile salvare la memoria", code = "SAVE_FAILED")
        }
        unified.pruneIfNeeded(UnifiedMemoryRepository.USER_FACING_MAX_ITEMS)
        return ToolResult.Success(
            data = mapOf(
                "success" to true,
                "memory_id" to id,
                "category" to category.name,
                "value" to value,
            ),
        )
    }

    private suspend fun executeLegacyOnly(
        invocation: ToolInvocation,
        legacy: UserMemoryRepository,
    ): ToolResult {
        val rawValue = (invocation.params["value"] as? String)?.trim().orEmpty()
        if (rawValue.isBlank()) {
            return ToolResult.Error(message = "Parametro 'value' mancante o vuoto", code = "MISSING_PARAM")
        }
        val value = RelativeDateNormalizer.normalize(rawValue)
        val category = MemoryToolSupport.parseCategory(invocation.params["category"]) ?: MemoryCategory.FACT
        val confidence = MemoryToolSupport.parseConfidence(invocation.params["confidence"])
        val ttlDays = MemoryToolSupport.parseTtlDays(invocation.params["ttl_days"])
        if (MemoryCategory.isUserFacing(category) && ttlDays != null) {
            return ToolResult.Error(message = "ttl_days applies only to OBSERVATION, INTENT, or PATTERN", code = "INVALID_PARAM")
        }
        if (MemoryCategory.isRobotInternal(category)) {
            return when (
                val result = legacy.upsertAutonomy(
                    category = category,
                    value = value,
                    confidence = confidence,
                    sourceMessageId = MemoryToolSupport.SOURCE_MESSAGE_LLM_TOOL,
                    ttlDays = ttlDays,
                )
            ) {
                is AutonomyUpsertResult.Success -> ToolResult.Success(
                    data = mapOf("success" to true, "memory_id" to result.memoryId, "category" to category.name, "value" to value),
                )
                AutonomyUpsertResult.IntentCapReached -> ToolResult.Error(
                    message = "Massimo ${UserMemoryRepository.MAX_ACTIVE_INTENTS} INTENT attivi",
                    code = "INTENT_CAP_REACHED",
                )
                AutonomyUpsertResult.InvalidValue -> ToolResult.Error(message = "Impossibile salvare la memoria", code = "SAVE_FAILED")
            }
        }
        val id = legacy.upsert(category, value, confidence, MemoryToolSupport.SOURCE_MESSAGE_LLM_TOOL)
        if (id < 0L) return ToolResult.Error(message = "Impossibile salvare la memoria", code = "SAVE_FAILED")
        legacy.pruneIfNeeded(DEFAULT_MAX_ITEMS)
        return ToolResult.Success(
            data = mapOf("success" to true, "memory_id" to id, "category" to category.name, "value" to value),
        )
    }

    companion object {
        private const val DEFAULT_MAX_ITEMS = UnifiedMemoryRepository.USER_FACING_MAX_ITEMS
    }
}
