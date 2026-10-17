package com.example.mydeskrobot.data.health

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HealthConnectExerciseSyncTest {

    private lateinit var activityLog: ActivityLogRepository
    private lateinit var reader: FakeHealthConnectExerciseReader
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        activityLog = ActivityLogRepository.createForTest(FakeActivityLogDao())
        reader = FakeHealthConnectExerciseReader()
    }

    @Test
    fun `same sourceChannel is not duplicated`() = runBlocking {
        reader.sessions = listOf(
            HealthConnectExerciseSession(
                metadataId = "bike-1",
                exerciseType = HealthConnectExerciseLabelMapper.TYPE_BIKING,
                startTimeMs = now - 60 * 60_000L,
                endTimeMs = now - 30 * 60_000L,
                title = "Bici",
            ),
        )
        val sync = HealthConnectExerciseSync(
            reader = reader,
            activityLogRepository = activityLog,
            episodeSink = activityLogSink(),
            clockMs = { now },
        )
        val first = sync.syncLastRetentionWindow()
        val second = sync.syncLastRetentionWindow()
        assertEquals(1, first.inserted)
        assertEquals(0, second.inserted)
        assertEquals(1, second.skippedDuplicate)
        assertEquals(1, activityLog.countEventsInRetentionWindow())
        assertNotNull(activityLog.findBySourceChannel("health_connect:bike-1"))
    }

    @Test
    fun `two different ids same day create two events`() = runBlocking {
        reader.sessions = listOf(
            HealthConnectExerciseSession(
                metadataId = "bike-a",
                exerciseType = HealthConnectExerciseLabelMapper.TYPE_BIKING,
                startTimeMs = now - 3 * 60 * 60_000L,
                endTimeMs = now - 2 * 60 * 60_000L,
            ),
            HealthConnectExerciseSession(
                metadataId = "bike-b",
                exerciseType = HealthConnectExerciseLabelMapper.TYPE_BIKING,
                startTimeMs = now - 90 * 60_000L,
                endTimeMs = now - 60 * 60_000L,
            ),
        )
        val sync = HealthConnectExerciseSync(
            reader = reader,
            activityLogRepository = activityLog,
            episodeSink = activityLogSink(),
            clockMs = { now },
        )
        val result = sync.syncLastRetentionWindow()
        assertEquals(2, result.inserted)
        assertEquals(2, activityLog.countEventsInRetentionWindow())
        assertNotNull(activityLog.findBySourceChannel("health_connect:bike-a"))
        assertNotNull(activityLog.findBySourceChannel("health_connect:bike-b"))
    }

    @Test
    fun `unavailable reader is no-op`() = runBlocking {
        reader.available = false
        reader.sessions = listOf(
            HealthConnectExerciseSession(
                metadataId = "x",
                exerciseType = HealthConnectExerciseLabelMapper.TYPE_WALKING,
                startTimeMs = now - 1_000L,
                endTimeMs = now,
            ),
        )
        val sync = HealthConnectExerciseSync(
            reader = reader,
            activityLogRepository = activityLog,
            episodeSink = activityLogSink(),
            clockMs = { now },
        )
        val result = sync.syncLastRetentionWindow()
        assertTrue(result.skippedUnavailable)
        assertEquals(0, result.inserted)
        assertEquals(0, activityLog.countEventsInRetentionWindow())
    }

    private fun activityLogSink(): HealthConnectEpisodeSink =
        object : HealthConnectEpisodeSink {
            override suspend fun writeExercise(
                label: String,
                rawPhrase: String,
                sourceChannel: String,
                startTimeMs: Long,
            ): Boolean {
                val id = activityLog.appendEvent(
                    label = label,
                    rawPhrase = rawPhrase,
                    source = ActivitySource.HEALTH_CONNECT,
                    timestampMs = startTimeMs,
                    sourceChannel = sourceChannel,
                )
                return id > 0L
            }
        }

    private class FakeHealthConnectExerciseReader : HealthConnectExerciseReader {
        var available: Boolean = true
        var hasPermission: Boolean = true
        var sessions: List<HealthConnectExerciseSession> = emptyList()

        override fun isAvailable(): Boolean = available
        override suspend fun hasExerciseReadPermission(): Boolean = hasPermission
        override suspend fun readSessions(startTimeMs: Long, endTimeMs: Long): List<HealthConnectExerciseSession> =
            sessions.filter { it.startTimeMs in startTimeMs..endTimeMs }
    }
}
