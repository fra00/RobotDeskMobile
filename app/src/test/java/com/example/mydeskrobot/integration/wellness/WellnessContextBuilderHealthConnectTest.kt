package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.wellness.WellnessDomains
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessContextBuilderHealthConnectTest {

    @Test
    fun `Today activities include Health Connect exercise labels`() = runTest {
        val now = System.currentTimeMillis()
        val activityLog = ActivityLogRepository.createForTest(FakeActivityLogDao())
        activityLog.appendEvent(
            label = "bici",
            rawPhrase = "Mattina · 45 min",
            source = ActivitySource.HEALTH_CONNECT,
            timestampMs = now - 60_000L,
            sourceChannel = "health_connect:bike-1",
        )
        val builder = WellnessContextBuilder(
            activityLogRepository = activityLog,
            unifiedMemoryRepository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao()),
        )
        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = false,
            bodyReachable = false,
            enabledDomainIds = setOf(WellnessDomains.MOVEMENT),
        )
        assertTrue(
            "expected Health Connect bike in recentDailyActivities, got=${input.recentDailyActivities}",
            input.recentDailyActivities.any { it.contains("bici", ignoreCase = true) },
        )
    }
}
