package com.example.mydeskrobot.data.health

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePreferringExerciseReaderTest {

    private class LocalFake : HealthConnectExerciseReader {
        var sessions: List<HealthConnectExerciseSession> = emptyList()
        override fun isAvailable(): Boolean = true
        override suspend fun hasExerciseReadPermission(): Boolean = true
        override suspend fun readSessions(startTimeMs: Long, endTimeMs: Long) = sessions
    }

    @Test
    fun `prefers remote sessions when fetch returns data`() = runBlocking {
        val local = LocalFake().apply {
            sessions = listOf(HealthConnectExerciseSession("local", 1, 1L, 2L))
        }
        val remote = listOf(HealthConnectExerciseSession("remote", 79, 10L, 20L, "Bike"))
        val reader = RemotePreferringExerciseReader(
            local = local,
            hasPairedProxy = { true },
            remoteFetch = { _, _ -> remote },
        )
        assertTrue(reader.hasExerciseReadPermission())
        assertEquals(remote, reader.readSessions(0L, 100L))
    }

    @Test
    fun `falls back to local when remote null`() = runBlocking {
        val localSession = HealthConnectExerciseSession("local", 1, 1L, 2L)
        val local = LocalFake().apply { sessions = listOf(localSession) }
        val reader = RemotePreferringExerciseReader(
            local = local,
            hasPairedProxy = { false },
            remoteFetch = { _, _ -> null },
        )
        assertEquals(listOf(localSession), reader.readSessions(0L, 100L))
    }
}
