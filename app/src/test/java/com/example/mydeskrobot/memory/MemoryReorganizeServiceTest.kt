package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.consolidate.MemoryConsolidationResult
import com.example.mydeskrobot.memory.consolidate.MemoryConsolidationService
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.llm.LlmResponse
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryReorganizeServiceTest {

    @Test
    fun runAutoIfDue_skips_when_auto_disabled() = runTest {
        val service = service(
            config = MemoryReorganizeConfig(autoReorganizeEnabled = false),
            llmConfigured = true,
        )

        assertEquals(MemoryReorganizeOutcome.SkippedAutoDisabled, service.runAutoIfDue())
    }

    @Test
    fun runManual_returns_gate_too_few() = runTest {
        val service = service(
            dao = FakeMemoryDocumentDao(listOf(userFact(1L, "Solo uno", MemoryCategory.FACT))),
            config = MemoryReorganizeConfig(minUserFacingRows = 100),
            llmConfigured = true,
        )

        val outcome = service.runManual()

        assertTrue(outcome is MemoryReorganizeOutcome.GateTooFew)
        assertEquals(1, (outcome as MemoryReorganizeOutcome.GateTooFew).count)
        assertEquals(100, outcome.minRequired)
    }

    @Test
    fun runManual_success_updates_last_reorganize_timestamp() = runTest {
        val settings = FakeReorganizeSettingsStore(
            config = MemoryReorganizeConfig(minUserFacingRows = 100),
        )
        val stub = StubLlmClient(
            response = Result.success(LlmResponse(buildConsolidationOutput(100))),
        )
        val service = service(
            dao = FakeMemoryDocumentDao(buildRows(100)),
            settings = settings,
            llm = stub,
            llmConfigured = true,
        )

        val outcome = service.runManual()

        assertTrue(outcome is MemoryReorganizeOutcome.Success)
        assertTrue(settings.lastReorganizeAtMs != null && settings.lastReorganizeAtMs!! > 0L)
    }

    @Test
    fun runManual_respects_custom_cooldown() = runTest {
        val last = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)
        val service = service(
            dao = FakeMemoryDocumentDao(buildRows(100)),
            settings = FakeReorganizeSettingsStore(
                config = MemoryReorganizeConfig(minUserFacingRows = 100, cooldownDays = 7),
                lastReorganizeAtMs = last,
            ),
            llmConfigured = true,
        )

        assertTrue(service.runManual() is MemoryReorganizeOutcome.GateCooldown)
    }

    @Test
    fun runManual_passes_force_and_min_rows_to_consolidation() = runTest {
        val stub = StubLlmClient(
            response = Result.success(LlmResponse(buildConsolidationOutput(100))),
        )
        val service = service(
            dao = FakeMemoryDocumentDao(buildRows(100)),
            config = MemoryReorganizeConfig(minUserFacingRows = 42),
            llm = stub,
            llmConfigured = true,
        )

        service.runManual(forceConsolidation = true)

        // Consolidation invoked (LLM called) with enough rows
        assertTrue(stub.lastUserMessage != null)
    }

    private fun service(
        dao: FakeMemoryDocumentDao = FakeMemoryDocumentDao(buildRows(100)),
        config: MemoryReorganizeConfig = MemoryReorganizeConfig(),
        settings: FakeReorganizeSettingsStore = FakeReorganizeSettingsStore(config),
        llm: StubLlmClient = StubLlmClient(
            response = Result.success(LlmResponse(buildConsolidationOutput(100))),
        ),
        llmConfigured: Boolean = true,
    ): MemoryReorganizeService {
        val repository = UnifiedMemoryRepository.createForTest(dao)
        val consolidation = MemoryConsolidationService(
            llmClient = llm,
            unifiedMemoryRepository = repository,
            settingsRepository = FakeConsolidationSettingsStore(),
            systemPrompt = "consolidate",
        )
        return MemoryReorganizeService(
            unifiedMemoryRepository = repository,
            consolidationService = consolidation,
            settingsRepository = settings,
            llmConfigured = { llmConfigured },
        )
    }

    private fun buildConsolidationOutput(count: Int): String =
        (0 until count).joinToString("\n") { "(FACT) Fatto archivio $it" }

    private fun buildRows(count: Int): List<MemoryDocumentEntity> =
        (0 until count).map { index -> userFact(index + 1L, "Fatto archivio $index", MemoryCategory.FACT) }

    private fun userFact(id: Long, value: String, category: MemoryCategory) =
        MemoryDocumentEntity(
            id = id,
            value = value,
            kind = MemoryDocumentKind.USER_FACT.name,
            category = category.name,
            source = MemoryDocumentSource.TOOL.name,
            confidence = 0.9f,
            createdAt = 1L,
            updatedAt = 1L,
        )

    private class FakeReorganizeSettingsStore(
        private val config: MemoryReorganizeConfig,
        lastReorganizeAtMs: Long? = null,
    ) : MemoryReorganizeSettingsStore {
        var lastReorganizeAtMs: Long? = lastReorganizeAtMs
            private set

        override suspend fun loadReorganizeConfig(): MemoryReorganizeConfig = config

        override suspend fun getLastManualReorganizeAtMs(): Long? = lastReorganizeAtMs

        override suspend fun setLastManualReorganizeAtMs(value: Long) {
            lastReorganizeAtMs = value
        }
    }

    private class FakeConsolidationSettingsStore : MemoryConsolidationSettingsStore {
        override suspend fun getLastConsolidatedContentHash(): String? = null
        override suspend fun setLastConsolidatedContentHash(hash: String) = Unit
        override suspend fun saveConsolidationBackup(items: List<MemoryItemEntity>) = Unit
    }

    private class StubLlmClient(
        private val configured: Boolean = true,
        private val response: Result<LlmResponse>,
    ) : LlmClient {
        var lastUserMessage: String? = null

        override fun isConfigured(): Boolean = configured

        override suspend fun chat(
            messages: List<ConversationMessage>,
            systemPrompt: String,
        ): Result<LlmResponse> {
            lastUserMessage = messages.filterIsInstance<ConversationMessage.User>().lastOrNull()?.content
            return response
        }

        override suspend fun chatWithImage(
            messages: List<ConversationMessage>,
            systemPrompt: String,
            imageBytes: ByteArray,
        ): Result<LlmResponse> = Result.failure(UnsupportedOperationException())
    }
}
