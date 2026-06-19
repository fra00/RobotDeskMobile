package com.example.mydeskrobot.data.activitylog

import com.example.mydeskrobot.domain.activitylog.ActivitySource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ActivityLogRepositoryTest {

    private lateinit var repository: ActivityLogRepository

    @Before
    fun setUp() {
        repository = ActivityLogRepository.createForTest(FakeActivityLogDao())
    }

    @Test
    fun `appendEvent deduplicates within 30 minutes`() = runBlocking {
        val now = System.currentTimeMillis()
        val first = repository.appendEvent("colazione", source = ActivitySource.TOOL, timestampMs = now)
        val second = repository.appendEvent("colazione", source = ActivitySource.TOOL, timestampMs = now + 5_000L)
        assertTrue(first > 0)
        assertEquals(first, second)
        assertEquals(1, repository.countEventsInRetentionWindow())
    }

    @Test
    fun `getEventsGroupedByDay groups by dayKey`() = runBlocking {
        val base = System.currentTimeMillis()
        repository.appendEvent("colazione", source = ActivitySource.TOOL, timestampMs = base)
        repository.appendEvent(
            "passeggiata",
            source = ActivitySource.EXTRACTOR,
            timestampMs = base - TimeUnit.DAYS.toMillis(1),
        )
        val groups = repository.getEventsGroupedByDay()
        assertEquals(2, groups.size)
        assertEquals(1, groups[0].events.size)
        assertEquals(1, groups[1].events.size)
    }

    @Test
    fun `pruneExpired removes events older than 7 days`() = runBlocking {
        val now = System.currentTimeMillis()
        val old = now - TimeUnit.DAYS.toMillis(8)
        val dao = FakeActivityLogDao(
            listOf(
                com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity(
                    id = 1L,
                    dayKey = ActivityLogRepository.dayKeyFor(old),
                    timestampMs = old,
                    label = "vecchia attività",
                    rawPhrase = null,
                    source = ActivitySource.TOOL,
                ),
                com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity(
                    id = 2L,
                    dayKey = ActivityLogRepository.dayKeyFor(now),
                    timestampMs = now,
                    label = "recente",
                    rawPhrase = null,
                    source = ActivitySource.TOOL,
                ),
            ),
        )
        val repo = ActivityLogRepository.createForTest(dao)
        val removed = repo.pruneExpired()
        assertEquals(1, removed)
        assertEquals(1, repo.countEventsInRetentionWindow())
    }

    @Test
    fun `normalizeLabel collapses whitespace`() {
        assertEquals("pausa caffè", ActivityLogRepository.normalizeLabel("  pausa   caffè  "))
    }

    @Test
    fun `upsertEpisodicEvent merges tentative to confirmed`() = runBlocking {
        val tomorrow = tomorrowDayKey()
        val first = repository.upsertEpisodicEvent(
            label = "cinema",
            rawPhrase = "domani cinema",
            source = ActivitySource.EXTRACTOR,
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PLAN,
            confidence = com.example.mydeskrobot.domain.activitylog.EpisodeConfidence.TENTATIVE,
            scheduledDayKey = tomorrow,
        )
        val second = repository.upsertEpisodicEvent(
            label = "cinema",
            rawPhrase = "alle 20:30",
            source = ActivitySource.EXTRACTOR,
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PLAN,
            confidence = com.example.mydeskrobot.domain.activitylog.EpisodeConfidence.CONFIRMED,
            scheduledDayKey = tomorrow,
            scheduledAtMs = ActivityLogRepository.parseScheduledAtMs(tomorrow, "20:30"),
        )
        assertEquals(first, second)
        val upcoming = repository.getUpcomingForDay(tomorrow)
        assertEquals(1, upcoming.size)
        assertEquals(com.example.mydeskrobot.domain.activitylog.EpisodeConfidence.CONFIRMED, upcoming[0].confidence)
        assertNotNull(upcoming[0].scheduledAtMs)
    }

    @Test
    fun `getUpcomingForDay filters by day and kind`() = runBlocking {
        val tomorrow = tomorrowDayKey()
        val today = ActivityLogRepository.dayKeyFor(System.currentTimeMillis())
        repository.upsertEpisodicEvent(
            label = "cinema",
            source = ActivitySource.EXTRACTOR,
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PLAN,
            scheduledDayKey = tomorrow,
        )
        repository.appendEvent(
            label = "colazione",
            source = ActivitySource.TOOL,
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PHYSICAL_NOW,
        )
        repository.upsertEpisodicEvent(
            label = "dentista",
            source = ActivitySource.EXTRACTOR,
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PLAN,
            scheduledDayKey = today,
        )
        assertEquals(1, repository.getUpcomingForDay(tomorrow).size)
        assertEquals("cinema", repository.getUpcomingForDay(tomorrow).first().label)
    }

    @Test
    fun `parseScheduledAtMs combines day and time`() {
        val ms = ActivityLogRepository.parseScheduledAtMs("2026-06-03", "20:30")
        assertNotNull(ms)
    }

    private fun tomorrowDayKey(): String {
        val calendar = java.util.Calendar.getInstance(java.util.Locale.ITALY)
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return ActivityLogRepository.dayKeyFor(calendar.timeInMillis)
    }
}
