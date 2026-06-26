package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.unified.db.MemoryDocumentDao
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryProjectionGuardTest {

    @Test
    fun projectAndVerify_succeeds_when_projection_matches() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        val guard = MemoryProjectionGuard(repository, settingsRepository = null)

        val result = guard.projectAndVerify(
            externalRef = "reminder:1",
            expectedActive = true,
        ) {
            repository.saveReminderProjection(
                taskId = 1L,
                message = "Prendi medicine",
                triggerAtMillis = System.currentTimeMillis() + 60_000L,
            )
        }

        assertTrue(result is MemoryProjectionGuard.ProjectionResult.Success)
    }

    @Test
    fun projectAndVerify_retries_once_then_records_drift() = runTest {
        val dao = DriftOnVerifyDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        val guard = MemoryProjectionGuard(repository, settingsRepository = null)

        val result = guard.projectAndVerify(
            externalRef = "reminder:2",
            expectedActive = true,
        ) {
            repository.saveReminderProjection(
                taskId = 2L,
                message = "Test",
                triggerAtMillis = System.currentTimeMillis() + 60_000L,
            )
        }

        assertTrue(result is MemoryProjectionGuard.ProjectionResult.Drift)
        assertEquals(2, dao.writeAttempts)
    }

    private class DriftOnVerifyDao : MemoryDocumentDao {
        private val delegate = FakeMemoryDocumentDao()
        var writeAttempts = 0

        override suspend fun upsert(entity: MemoryDocumentEntity): Long {
            writeAttempts++
            return delegate.upsert(entity)
        }

        override suspend fun update(entity: MemoryDocumentEntity) = delegate.update(entity)
        override suspend fun getById(id: Long): MemoryDocumentEntity? = delegate.getById(id)
        override suspend fun getByExternalRef(externalRef: String): MemoryDocumentEntity? = null
        override suspend fun getAllActive(now: Long): List<MemoryDocumentEntity> = delegate.getAllActive(now)
        override suspend fun countActive(): Int = delegate.countActive()
        override suspend fun deactivateById(id: Long, now: Long) = delegate.deactivateById(id, now)
        override suspend fun deactivateByExternalRef(externalRef: String, now: Long) =
            delegate.deactivateByExternalRef(externalRef, now)
        override suspend fun markUsed(ids: List<Long>, now: Long) = delegate.markUsed(ids, now)
        override suspend fun getActiveByKind(kind: String, limit: Int, now: Long) =
            delegate.getActiveByKind(kind, limit, now)
        override suspend fun getActiveByKindAndScheduledDay(
            kind: String,
            scheduledDayKey: String,
            now: Long,
        ) = delegate.getActiveByKindAndScheduledDay(kind, scheduledDayKey, now)

        override suspend fun getUnreadByKind(kind: String, limit: Int, now: Long) =
            delegate.getUnreadByKind(kind, limit, now)

        override suspend fun markReadByExternalRef(externalRef: String, now: Long) =
            delegate.markReadByExternalRef(externalRef, now)

        override suspend fun markAllUnreadByKind(kind: String, now: Long): Int =
            delegate.markAllUnreadByKind(kind, now)
    }
}
