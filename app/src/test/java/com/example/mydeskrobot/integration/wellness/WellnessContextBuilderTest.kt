package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
import com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.wellness.WellnessDomains
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WellnessContextBuilderTest {

    private val now = System.currentTimeMillis()

    @Test
    fun `build domain score includes fresh order observation`() = runTest {
        val builder = createBuilder(
            observations = listOf(
                observation("Meteo piovoso in città"),
                observation("Scrivania disordinata con documenti sparsi"),
            ),
        )

        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = true,
            bodyReachable = true,
        )

        assertEquals(WellnessPhase.DOMAIN_SCORE, input.phase)
        assertEquals(
            "Scrivania disordinata con documenti sparsi",
            input.orderObservationFresh,
        )
        assertTrue(input.recentObservations.size >= 2)
    }

    @Test
    fun `build domain score null order when no clutter keywords`() = runTest {
        val builder = createBuilder(
            observations = listOf(
                observation("Utente ha pranzato alle 13"),
                observation("Conversazione su meteo"),
            ),
        )

        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = false,
            bodyReachable = false,
        )

        assertNull(input.orderObservationFresh)
    }

    @Test
    fun `build domain score omits order observation when order domain disabled`() = runTest {
        val builder = createBuilder(
            observations = listOf(
                observation("Scrivania disordinata con documenti sparsi"),
            ),
        )

        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = true,
            bodyReachable = true,
            enabledDomainIds = setOf("pasti"),
        )

        assertNull(input.orderObservationFresh)
        assertEquals(setOf("pasti"), input.enabledDomainIds)
    }

    @Test
    fun `build domain score includes custom domain prompts`() = runTest {
        val builder = createBuilder(observations = emptyList())
        val custom = com.example.mydeskrobot.domain.wellness.WellnessCustomDomain(
            id = "custom_idratazione",
            displayName = "Idratazione",
            prompt = "Verifica se l'utente ha bevuto abbastanza acqua oggi",
        )

        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = false,
            bodyReachable = false,
            enabledDomainIds = setOf("pasti", "custom_idratazione"),
            customDomains = listOf(custom),
        )

        assertEquals(1, input.customDomains.size)
        assertEquals("Idratazione", input.customDomains.first().displayName)
        assertTrue(input.enabledDomainIds.contains("custom_idratazione"))
    }

    @Test
    fun `build visual phase omits order observation`() = runTest {
        val builder = createBuilder(
            observations = listOf(observation("Ordine scrivania: clutter moderato")),
        )

        val input = builder.build(
            phase = WellnessPhase.VISUAL_ORDER,
            bodyConfigured = true,
            bodyReachable = true,
        )

        assertEquals(WellnessPhase.VISUAL_ORDER, input.phase)
        assertNull(input.orderObservationFresh)
    }

    @Test
    fun `build domain score separates social and work threads`() = runTest {
        val dayKey = dayKeyFormat.format(Date(now))
        val activityDao = FakeActivityLogDao(
            listOf(
                ActivityLogEventEntity(
                    dayKey = dayKey,
                    timestampMs = now,
                    label = "Marco",
                    rawPhrase = "ciao",
                    source = ActivitySource.NOTIFICATION,
                    eventKind = EpisodeKind.SOCIAL_THREAD,
                    actor = "Marco",
                    sourceChannel = "WhatsApp",
                ),
                ActivityLogEventEntity(
                    dayKey = dayKey,
                    timestampMs = now - 1_000L,
                    label = "Luca",
                    rawPhrase = "sync?",
                    source = ActivitySource.NOTIFICATION,
                    eventKind = EpisodeKind.WORK_THREAD,
                    actor = "Luca",
                    sourceChannel = "Microsoft Teams",
                ),
            ),
        )
        val builder = WellnessContextBuilder(
            activityLogRepository = ActivityLogRepository.createForTest(activityDao),
            unifiedMemoryRepository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao()),
        )

        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = false,
            bodyReachable = false,
            enabledDomainIds = setOf(WellnessDomains.SOCIAL, WellnessDomains.WORKLOAD),
        )

        assertTrue(input.recentSocialThreads.any { it.contains("Marco") && it.contains("WhatsApp") })
        assertTrue(input.recentWorkThreads.any { it.contains("Luca") && it.contains("Microsoft Teams") })
        assertTrue(input.recentSocialThreads.none { it.contains("Teams") })
        assertTrue(input.recentWorkThreads.none { it.contains("WhatsApp") })
    }

    @Test
    fun `build formats habit slots as expected habits not raw json`() = runTest {
        val slot = com.example.mydeskrobot.domain.predictivity.HabitSlot(
            slotKey = "tapis_roulant|1140",
            canonicalLabel = "tapis_roulant",
            displayLabel = "tapis roulant",
            typicalTimeMinutes = 1140,
            hitCount = 5,
            confidence = 0.8f,
        )
        val patternDoc = MemoryDocumentEntity(
            id = 99L,
            value = com.example.mydeskrobot.data.predictivity.HabitSlotCodec.encode(slot),
            kind = MemoryDocumentKind.AUTONOMY.name,
            category = MemoryCategory.PATTERN.name,
            source = MemoryDocumentSource.EXTRACTOR.name,
            confidence = 0.8f,
            createdAt = now,
            updatedAt = now,
        )
        val builder = createBuilder(observations = listOf(patternDoc))

        val input = builder.build(
            phase = WellnessPhase.DOMAIN_SCORE,
            bodyConfigured = false,
            bodyReachable = false,
        )

        assertTrue(input.activePatterns.any { it.contains("tapis roulant") && it.contains("19:00") })
        assertTrue(input.activePatterns.none { it.contains("HABIT_SLOT:v1:") })
    }

    private fun createBuilder(observations: List<MemoryDocumentEntity>): WellnessContextBuilder {
        val unified = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao(observations))
        val activityLog = ActivityLogRepository.createForTest(FakeActivityLogDao())
        return WellnessContextBuilder(
            activityLogRepository = activityLog,
            unifiedMemoryRepository = unified,
        )
    }

    private fun observation(value: String): MemoryDocumentEntity =
        MemoryDocumentEntity(
            id = value.hashCode().toLong(),
            value = value,
            kind = MemoryDocumentKind.AUTONOMY.name,
            category = MemoryCategory.OBSERVATION.name,
            source = MemoryDocumentSource.TOOL.name,
            confidence = 0.9f,
            createdAt = now,
            updatedAt = now,
        )

    companion object {
        private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
    }
}
