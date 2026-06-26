package com.example.mydeskrobot.memory.unified

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryProjectionReconcilerTest {

    @Test
    fun verifyProjection_detects_active_and_inactive_states() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)

        assertTrue(repository.verifyProjection("reminder:1", expectedActive = false))

        repository.saveReminderProjection(
            taskId = 1L,
            message = "Chiamare Marco",
            triggerAtMillis = System.currentTimeMillis() + 120_000L,
        )
        assertTrue(repository.verifyProjection("reminder:1", expectedActive = true))

        repository.deactivateReminderProjection(1L)
        assertTrue(repository.verifyProjection("reminder:1", expectedActive = false))
        assertFalse(repository.verifyProjection("reminder:1", expectedActive = true))
    }
}
