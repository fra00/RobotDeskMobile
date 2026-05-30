package com.example.mydeskrobot.data.scheduled

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduledTaskRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = ScheduledTaskRepository.createInMemory(context)

    @Test
    fun schedule_list_and_cancel() = runBlocking {
        val trigger = System.currentTimeMillis() + 60_000L
        val id = repository.schedule("Test message", trigger)

        val pending = repository.listPending()
        assertEquals(1, pending.size)
        assertEquals("Test message", pending.first().message)

        assertTrue(repository.cancel(id))
        assertEquals(0, repository.listPending().size)
    }

    @Test
    fun markFired_updates_status() = runBlocking {
        val id = repository.schedule("Fired", System.currentTimeMillis() + 120_000L)
        repository.markFired(id)
        val task = repository.getById(id)
        assertEquals(ScheduledTaskStatus.FIRED, task?.status)
    }
}
