package com.example.mydeskrobot.memory

import com.example.mydeskrobot.integration.memory.FakeMemoryDao
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UserMemoryRepositoryAutonomyTest {

    @Test
    fun upsertAutonomy_rejectsFourthIntent() = runBlocking {
        val dao = FakeMemoryDao()
        val repository = UserMemoryRepository.createForTest(dao)

        repeat(3) { index ->
            val result = repository.upsertAutonomy(
                category = MemoryCategory.INTENT,
                value = "INTENT test $index",
            )
            assertTrue(result is AutonomyUpsertResult.Success)
        }

        val fourth = repository.upsertAutonomy(
            category = MemoryCategory.INTENT,
            value = "INTENT test fourth",
        )
        assertEquals(AutonomyUpsertResult.IntentCapReached, fourth)
    }

    @Test
    fun pruneExpired_softDeletesExpiredRows() = runBlocking {
        val expiredAt = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        val dao = FakeMemoryDao(
            listOf(
                entity(
                    id = 1L,
                    category = MemoryCategory.OBSERVATION,
                    value = "expired note",
                    expiresAt = expiredAt,
                ),
                entity(
                    id = 2L,
                    category = MemoryCategory.FACT,
                    value = "still valid",
                ),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        val pruned = repository.pruneExpired()

        assertEquals(1, pruned)
        assertEquals(1, repository.getAllActive().size)
        assertEquals(MemoryCategory.FACT, repository.getAllActive().first().category)
    }

    @Test
    fun getUserFacingActive_excludesRobotInternal() = runBlocking {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.FACT, "fact"),
                entity(2L, MemoryCategory.INTENT, "INTENT hidden"),
                entity(3L, MemoryCategory.OBSERVATION, "observation hidden"),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        val userFacing = repository.getUserFacingActive()

        assertEquals(1, userFacing.size)
        assertEquals(MemoryCategory.FACT, userFacing.first().category)
    }

    @Test
    fun getActivePatterns_returnsOnlyPatternCategory() = runBlocking {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.PATTERN, "PATTERN: lunch skip"),
                entity(2L, MemoryCategory.INTENT, "INTENT a"),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        val patterns = repository.getActivePatterns()

        assertEquals(1, patterns.size)
        assertEquals("PATTERN: lunch skip", patterns.first().value)
    }

    @Test
    fun getActiveIntents_returnsOnlyIntentCategory() = runBlocking {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.INTENT, "INTENT a"),
                entity(2L, MemoryCategory.OBSERVATION, "note"),
            ),
        )
        val repository = UserMemoryRepository.createForTest(dao)

        val intents = repository.getActiveIntents()

        assertEquals(1, intents.size)
        assertEquals("INTENT a", intents.first().value)
    }

    private fun entity(
        id: Long,
        category: MemoryCategory,
        value: String,
        expiresAt: Long? = null,
    ) = MemoryItemEntity(
        id = id,
        category = category,
        value = value,
        confidence = 0.85f,
        createdAt = 1L,
        updatedAt = 1L,
        sourceMessageId = 0L,
        expiresAt = expiresAt,
    )
}
