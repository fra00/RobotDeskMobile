package com.example.mydeskrobot.memory.consolidate

import com.example.mydeskrobot.memory.MemoryConsolidationSettingsStore
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.example.mydeskrobot.reasoning.llm.LlmResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryConsolidationServiceTest {

    @Test
    fun consolidateIfNeeded_skips_when_fewer_than_min_non_pinned_rows() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                userFact(1L, "Fatto uno", MemoryCategory.FACT),
                userFact(2L, "Fatto due", MemoryCategory.FACT, isPinned = true),
            ),
        )
        val service = service(dao, StubLlmClient(configured = true))

        val result = service.consolidateIfNeeded(force = true)

        assertTrue(result is MemoryConsolidationResult.SkippedTooFew)
        assertEquals(1, (result as MemoryConsolidationResult.SkippedTooFew).count)
    }

    @Test
    fun consolidateIfNeeded_excludes_pinned_from_llm_payload() = runTest {
        val dao = FakeMemoryDocumentDao(
            buildConsolidatableRows(startId = 1L, count = 100) + listOf(
                userFact(9_999L, "L'utente si chiama Francesco", MemoryCategory.IDENTITY, isPinned = true),
            ),
        )
        val stub = StubLlmClient(
            configured = true,
            response = Result.success(
                LlmResponse(buildMinimalConsolidationOutput(100)),
            ),
        )
        val service = service(dao, stub)

        service.consolidateIfNeeded(force = true)

        assertTrue(stub.lastUserMessage != null)
        assertTrue(stub.lastUserMessage!!.contains("Fatto archivio"))
        assertTrue(!stub.lastUserMessage!!.contains("Francesco"))
    }

    private fun buildMinimalConsolidationOutput(count: Int): String =
        (0 until count).joinToString("\n") { index ->
            "(FACT) Fatto archivio $index"
        }

    private fun service(
        dao: FakeMemoryDocumentDao,
        llm: StubLlmClient,
    ): MemoryConsolidationService {
        val repository = UnifiedMemoryRepository.createForTest(dao)
        return MemoryConsolidationService(
            llmClient = llm,
            unifiedMemoryRepository = repository,
            settingsRepository = FakeMemorySettingsStore(),
            systemPrompt = "consolidate",
        )
    }

    private fun buildConsolidatableRows(startId: Long, count: Int): List<MemoryDocumentEntity> =
        (0 until count).map { index ->
            userFact(
                id = startId + index,
                value = "Fatto archivio $index",
                category = MemoryCategory.FACT,
            )
        }

    private fun userFact(
        id: Long,
        value: String,
        category: MemoryCategory,
        isPinned: Boolean = false,
    ) = MemoryDocumentEntity(
        id = id,
        value = value,
        kind = MemoryDocumentKind.USER_FACT.name,
        category = category.name,
        source = MemoryDocumentSource.TOOL.name,
        confidence = 0.9f,
        createdAt = 1L,
        updatedAt = 1L,
        isPinned = isPinned,
    )

    private class StubLlmClient(
        private val configured: Boolean = true,
        private val response: Result<LlmResponse> = Result.success(LlmResponse("(FACT) merged")),
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

    private class FakeMemorySettingsStore : MemoryConsolidationSettingsStore {
        private var hash: String? = null

        override suspend fun getLastConsolidatedContentHash(): String? = hash

        override suspend fun setLastConsolidatedContentHash(hash: String) {
            this.hash = hash
        }

        override suspend fun saveConsolidationBackup(items: List<MemoryItemEntity>) = Unit
    }
}
