package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
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
}
