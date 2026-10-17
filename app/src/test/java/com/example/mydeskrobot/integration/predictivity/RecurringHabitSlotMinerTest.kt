package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
import com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.data.predictivity.PredictivityMiningStore
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotKey
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RecurringHabitSlotMinerTest {

    private val tz = Calendar.getInstance().timeZone

    @Test
    fun `minePendingDays upserts slot and advances watermark without LLM`() = runTest {
        val day1Key = dayKeyOffset(daysAgo = 3)
        val day2Key = dayKeyOffset(daysAgo = 2)
        val ts1 = timestampForDayKey(day1Key, 8, 30)
        val ts2 = timestampForDayKey(day2Key, 8, 35)
        val ts3 = timestampForDayKey(day2Key, 8, 40)

        val dao = FakeActivityLogDao(
            listOf(
                event(dayKey = day1Key, label = "passeggiata cane", timestampMs = ts1),
                event(dayKey = day2Key, label = "passeggiata cane", timestampMs = ts2),
                event(dayKey = day2Key, label = "passeggiata cane", timestampMs = ts3),
            ),
        )
        val activityLog = ActivityLogRepository.createForTest(dao)
        val unifiedMemory = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        val habitSlots = HabitSlotRepository(unifiedMemory)
        val miningStore = FakeMiningStore(lastMined = null)

        val miner = RecurringHabitSlotMiner(
            activityLogRepository = activityLog,
            habitSlotRepository = habitSlots,
            miningRepository = miningStore,
            labelNormalizer = HabitLabelNormalizer(
                llmClient = object : com.example.mydeskrobot.reasoning.llm.LlmClient {
                    override suspend fun chat(
                        messages: List<com.example.mydeskrobot.reasoning.model.ConversationMessage>,
                        systemPrompt: String,
                    ): Result<com.example.mydeskrobot.reasoning.llm.LlmResponse> =
                        Result.failure(IllegalStateException("offline"))

                    override suspend fun chatWithImage(
                        messages: List<com.example.mydeskrobot.reasoning.model.ConversationMessage>,
                        systemPrompt: String,
                        imageBytes: ByteArray,
                    ): Result<com.example.mydeskrobot.reasoning.llm.LlmResponse> =
                        Result.failure(IllegalStateException("offline"))

                    override fun isConfigured() = false
                },
                normalizePrompt = "test",
            ),
        )

        val result = miner.minePendingDays()

        assertEquals(2, result.daysProcessed)
        assertEquals(day2Key, miningStore.lastMined)
        val slot = habitSlots.findBySlotKey(
            HabitSlotKey.buildSlotKey("passeggiata_cane", HabitSlotKey.timeBucketMinutes(ts1)),
        )
        assertTrue(slot != null)
        assertEquals(2, slot!!.hitCount)
        assertEquals(0.257f, slot.confidence, 0.02f)
    }

    @Test
    fun `minePendingDays merges evening meal fragments on consolidate`() = runTest {
        val unifiedMemory = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        val habitSlots = HabitSlotRepository(unifiedMemory)
        // Pre-seed fragmented dinner-like slots (as seen in UI before hybrid merge).
        listOf(
            HabitSlot(
                slotKey = "mangiare_qualcosa|1230",
                canonicalLabel = "mangiare_qualcosa",
                displayLabel = "Mangiare qualcosa",
                typicalTimeMinutes = 20 * 60 + 54,
                hitCount = 1,
                confidence = 0.13f,
                rawLabels = setOf("Mangiare qualcosa"),
            ),
            HabitSlot(
                slotKey = "pranzo|1290",
                canonicalLabel = "pranzo",
                displayLabel = "Pranzo",
                typicalTimeMinutes = 21 * 60 + 51,
                hitCount = 1,
                confidence = 0.13f,
                rawLabels = setOf("Pranzo"),
            ),
            HabitSlot(
                slotKey = "cena|1245",
                canonicalLabel = "cena",
                displayLabel = "Cena",
                typicalTimeMinutes = 20 * 60 + 45,
                hitCount = 2,
                confidence = 0.26f,
                rawLabels = setOf("Cena"),
            ),
        ).forEach { habitSlots.upsert(it) }

        val dao = FakeActivityLogDao(emptyList())
        val miner = RecurringHabitSlotMiner(
            activityLogRepository = ActivityLogRepository.createForTest(dao),
            habitSlotRepository = habitSlots,
            miningRepository = FakeMiningStore(lastMined = dayKeyOffset(daysAgo = 1)),
            labelNormalizer = HabitLabelNormalizer(
                llmClient = object : com.example.mydeskrobot.reasoning.llm.LlmClient {
                    override suspend fun chat(
                        messages: List<com.example.mydeskrobot.reasoning.model.ConversationMessage>,
                        systemPrompt: String,
                    ) = Result.failure<com.example.mydeskrobot.reasoning.llm.LlmResponse>(
                        IllegalStateException("offline"),
                    )

                    override suspend fun chatWithImage(
                        messages: List<com.example.mydeskrobot.reasoning.model.ConversationMessage>,
                        systemPrompt: String,
                        imageBytes: ByteArray,
                    ) = Result.failure<com.example.mydeskrobot.reasoning.llm.LlmResponse>(
                        IllegalStateException("offline"),
                    )

                    override fun isConfigured() = false
                },
                normalizePrompt = "test",
            ),
        )

        miner.minePendingDays()

        val remaining = habitSlots.listAll()
        assertEquals(1, remaining.size)
        assertEquals("cena", remaining.single().canonicalLabel)
        assertEquals(4, remaining.single().hitCount)
    }

    private class FakeMiningStore(var lastMined: String?) : PredictivityMiningStore {
        override suspend fun getLastMinedDayKey(): String? = lastMined
        override suspend fun setLastMinedDayKey(dayKey: String) {
            lastMined = dayKey
        }
    }

    private fun dayKeyOffset(daysAgo: Int): String {
        val calendar = Calendar.getInstance(tz).apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        return ActivityLogRepository.dayKeyFor(calendar.timeInMillis)
    }

    private fun timestampForDayKey(dayKey: String, hour: Int, minute: Int): Long {
        val parts = dayKey.split("-").map { it.toInt() }
        return Calendar.getInstance(tz).apply {
            set(parts[0], parts[1] - 1, parts[2], hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun event(
        dayKey: String,
        label: String,
        timestampMs: Long,
    ) = ActivityLogEventEntity(
        dayKey = dayKey,
        timestampMs = timestampMs,
        label = label,
        rawPhrase = null,
        source = ActivitySource.TOOL,
        eventKind = EpisodeKind.PHYSICAL_NOW,
        confidence = EpisodeConfidence.CONFIRMED,
    )
}
