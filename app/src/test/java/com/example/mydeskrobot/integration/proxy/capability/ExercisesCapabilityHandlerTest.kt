package com.example.mydeskrobot.integration.proxy.capability

import com.example.mydeskrobot.data.health.HealthConnectExerciseReader
import com.example.mydeskrobot.data.health.HealthConnectExerciseSession
import com.example.mydeskrobot.integration.proxy.capability.handlers.ExercisesCapabilityHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisesCapabilityHandlerTest {

    private class FakeReader : HealthConnectExerciseReader {
        var available: Boolean = true
        var permission: Boolean = true
        var sessions: List<HealthConnectExerciseSession> = emptyList()

        override fun isAvailable(): Boolean = available
        override suspend fun hasExerciseReadPermission(): Boolean = permission
        override suspend fun readSessions(startTimeMs: Long, endTimeMs: Long): List<HealthConnectExerciseSession> =
            sessions
    }

    @Test
    fun `returns sessions json`() = runBlocking {
        val reader = FakeReader().apply {
            sessions = listOf(
                HealthConnectExerciseSession(
                    metadataId = "s1",
                    exerciseType = 79,
                    startTimeMs = 1000L,
                    endTimeMs = 2000L,
                    title = "Run",
                ),
            )
        }
        val handler = ExercisesCapabilityHandler(reader)
        val json = handler.handle(mapOf("fromMs" to "0", "toMs" to "5000"))
        assertTrue(json.optBoolean("available"))
        assertTrue(json.optBoolean("permission"))
        assertEquals(1, json.getJSONArray("sessions").length())
        assertEquals("s1", json.getJSONArray("sessions").getJSONObject(0).getString("metadataId"))
    }

    @Test(expected = PhoneCapabilityException::class)
    fun `missing fromMs throws`(): Unit = runBlocking {
        ExercisesCapabilityHandler(FakeReader()).handle(mapOf("toMs" to "1"))
        Unit
    }
}
