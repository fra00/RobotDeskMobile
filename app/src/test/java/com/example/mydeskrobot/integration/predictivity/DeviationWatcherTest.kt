package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
import com.example.mydeskrobot.data.activitylog.db.ActivityLogEventEntity
import com.example.mydeskrobot.data.body.BodySettings
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.predictivity.HabitSlotRepository
import com.example.mydeskrobot.data.presence.DeskPresenceSettings
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotKey
import com.example.mydeskrobot.data.proactive.ProactivitySettings
import com.example.mydeskrobot.integration.input.heartbeat.ProactiveGatePolicy
import com.example.mydeskrobot.integration.presence.BodyLocateService
import com.example.mydeskrobot.integration.presence.UserAttentionCentering
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class DeviationWatcherTest {

    private val fixedNow: Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 35)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `findCandidate returns slot in window without matching episode`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val context = watchContext(lastUserTurnMs = fixedNow - 60_000L)

        assertEquals(slot.slotKey, watcher.findCandidate(context)?.slotKey)
    }

    @Test
    fun `findCandidate skips when episode exists today`() = runTest {
        val slot = eligibleSlot()
        val todayKey = ActivityLogRepository.dayKeyFor(fixedNow)
        val watcher = createWatcher(
            slots = listOf(slot),
            episodes = listOf(
                ActivityLogEventEntity(
                    dayKey = todayKey,
                    timestampMs = fixedNow,
                    label = "passeggiata cane",
                    rawPhrase = null,
                    source = ActivitySource.TOOL,
                    eventKind = EpisodeKind.PHYSICAL_NOW,
                    confidence = EpisodeConfidence.CONFIRMED,
                ),
            ),
        )

        assertNull(watcher.findCandidate(watchContext(lastUserTurnMs = fixedNow - 60_000L)))
    }

    @Test
    fun `findCandidate skips already asked slot today`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val wm = WorkingMemory.forToday()
            .withDeviationAsked(slot.slotKey)
            .withUserTurn(fixedNow - 60_000L)

        assertNull(watcher.findCandidate(watchContext(workingMemory = wm)))
    }

    @Test
    fun `findCandidate skips when episode within tolerance today`() = runTest {
        val slot = eligibleSlot()
        val todayKey = ActivityLogRepository.dayKeyFor(fixedNow)
        val episodeAt845 = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            set(Calendar.MINUTE, 45)
        }.timeInMillis
        val watcher = createWatcher(
            slots = listOf(slot),
            episodes = listOf(
                ActivityLogEventEntity(
                    dayKey = todayKey,
                    timestampMs = episodeAt845,
                    label = "passeggiata cane",
                    rawPhrase = null,
                    source = ActivitySource.TOOL,
                    eventKind = EpisodeKind.PHYSICAL_NOW,
                    confidence = EpisodeConfidence.CONFIRMED,
                ),
            ),
        )

        assertNull(watcher.findCandidate(watchContext(lastUserTurnMs = fixedNow - 60_000L)))
    }

    @Test
    fun `findCandidate returns slot when episode outside tolerance today`() = runTest {
        val slot = eligibleSlot()
        val todayKey = ActivityLogRepository.dayKeyFor(fixedNow)
        val episodeLate = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 30)
        }.timeInMillis
        val watcher = createWatcher(
            slots = listOf(slot),
            episodes = listOf(
                ActivityLogEventEntity(
                    dayKey = todayKey,
                    timestampMs = episodeLate,
                    label = "passeggiata cane",
                    rawPhrase = null,
                    source = ActivitySource.TOOL,
                    eventKind = EpisodeKind.PHYSICAL_NOW,
                    confidence = EpisodeConfidence.CONFIRMED,
                ),
            ),
        )

        assertEquals(slot.slotKey, watcher.findCandidate(watchContext(lastUserTurnMs = fixedNow - 60_000L))?.slotKey)
    }

    @Test
    fun `findCandidate skips when mic session inactive`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val context = watchContext(lastUserTurnMs = fixedNow - 60_000L).copy(micSessionActive = false)

        assertNull(watcher.findCandidate(context))
    }

    @Test
    fun `findCandidate proceeds when micro-tick switch disabled`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val context = watchContext(lastUserTurnMs = fixedNow - 60_000L).copy(
            heartbeatSettings = HeartbeatSettings(enabled = false),
        )

        assertEquals(slot.slotKey, watcher.findCandidate(context)?.slotKey)
    }

    @Test
    fun `findCandidate skips on silent robot context`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val context = watchContext(lastUserTurnMs = fixedNow - 60_000L).copy(
            robotContext = RobotContextState(
                profile = RobotProfile.WORK,
                notificationMode = NotificationMode.SILENT,
            ),
        )

        assertNull(watcher.findCandidate(context))
    }

    @Test
    fun `findCandidate skips when daily proactive cap reached`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        var wm = WorkingMemory.forToday().withUserTurn(fixedNow - 60_000L)
        repeat(ProactiveGatePolicy.MAX_PROACTIVE_SPEAKS_PER_DAY) {
            wm = wm.withProactiveSpeak(fixedNow - 60_000L)
        }

        assertNull(watcher.findCandidate(watchContext(workingMemory = wm)))
    }

    @Test
    fun `findCandidate skips when slot outside deviation window`() = runTest {
        val slot = eligibleSlot().copy(typicalTimeMinutes = 600)
        val watcher = createWatcher(listOf(slot), episodes = emptyList())

        assertNull(watcher.findCandidate(watchContext(lastUserTurnMs = fixedNow - 60_000L)))
    }

    @Test
    fun `findCandidate skips when user not present`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val context = watchContext(lastUserTurnMs = null).copy(
            bodyConfigured = true,
            bodyReachable = true,
        )

        assertNull(watcher.findCandidate(context))
    }

    @Test
    fun `findCandidate skips when predictivity disabled`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val context = watchContext(
            lastUserTurnMs = fixedNow - 60_000L,
            predictivityEnabled = false,
        )

        assertNull(watcher.findCandidate(context))
    }

    @Test
    fun `findCandidate skips suppressed slot today`() = runTest {
        val slot = eligibleSlot()
        val watcher = createWatcher(listOf(slot), episodes = emptyList())
        val wm = WorkingMemory.forToday()
            .withUserTurn(fixedNow - 60_000L)
            .withDeviationSuppressed(slot.slotKey)

        assertNull(watcher.findCandidate(watchContext(workingMemory = wm)))
    }

    private suspend fun createWatcher(
        slots: List<HabitSlot>,
        episodes: List<ActivityLogEventEntity>,
    ): DeviationWatcher {
        val unified = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        val habitRepo = HabitSlotRepository(unified)
        slots.forEach { habitRepo.upsert(it) }
        val activityLog = ActivityLogRepository.createForTest(FakeActivityLogDao(episodes))
        val bodyLocate = BodyLocateService(
            bodySettingsProvider = { BodySettings() },
            attentionCentering = UserAttentionCentering(
                bodySettingsProvider = { BodySettings() },
                deskPresenceSettingsProvider = { DeskPresenceSettings(enabled = false) },
            ),
        )
        return DeviationWatcher(
            habitSlotRepository = habitRepo,
            activityLogRepository = activityLog,
            bodyLocateService = bodyLocate,
            nowMillis = { fixedNow },
        )
    }

    private fun watchContext(
        lastUserTurnMs: Long? = fixedNow - 60_000L,
        workingMemory: WorkingMemory? = null,
        predictivityEnabled: Boolean = true,
    ): DeviationWatchContext {
        val wm = workingMemory ?: WorkingMemory.forToday().let {
            if (lastUserTurnMs != null) it.withUserTurn(lastUserTurnMs) else it
        }
        return DeviationWatchContext(
            heartbeatSettings = HeartbeatSettings(enabled = true),
            proactivitySettings = ProactivitySettings(predictivityEnabled = predictivityEnabled),
            workingMemory = wm,
            robotContext = null,
            bodyConfigured = false,
            bodyReachable = false,
            micSessionActive = true,
        )
    }

    private fun eligibleSlot(): HabitSlot {
        val typical = HabitSlotKey.minutesSinceMidnight(fixedNow)
        val bucket = HabitSlotKey.timeBucketMinutes(fixedNow)
        return HabitSlot(
            slotKey = HabitSlotKey.buildSlotKey("passeggiata_cane", bucket),
            canonicalLabel = "passeggiata_cane",
            displayLabel = "Passeggiata cane",
            typicalTimeMinutes = typical,
            hitCount = 5,
            confidence = 0.75f,
            rawLabels = setOf("passeggiata cane"),
        )
    }
}
