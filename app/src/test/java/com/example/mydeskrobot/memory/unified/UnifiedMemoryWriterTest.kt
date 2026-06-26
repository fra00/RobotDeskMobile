package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.activitylog.FakeActivityLogDao
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedMemoryWriterTest {

    @Test
    fun saveEpisode_is_visible_in_recall_without_reconcile() = runTest {
        val unifiedDao = FakeMemoryDocumentDao()
        val unifiedRepository = UnifiedMemoryRepository.createForTest(unifiedDao)
        val activityDao = FakeActivityLogDao()
        val activityRepository = ActivityLogRepository.createForTest(activityDao)
        val writer = UnifiedMemoryWriter(unifiedRepository, activityRepository, settingsRepository = null)

        val dayKey = ActivityLogRepository.dayKeyFor(System.currentTimeMillis())
        val result = writer.saveEpisode(
            label = "passeggiata serale",
            rawPhrase = "sono uscito a camminare",
            source = ActivitySource.TOOL,
            eventKind = EpisodeKind.PHYSICAL_NOW,
            memorySource = MemoryDocumentSource.TOOL,
        )

        assertTrue(result.indexOk)
        assertTrue(result.eventId >= 0L)

        val recalled = unifiedRepository.recallForQuestion(
            MemoryRecallRequest(
                query = "passeggiata",
                temporalScope = TemporalScope.SINGLE_DAY,
                focusDayKey = dayKey,
            ),
        )
        assertTrue(recalled.any { it.value.contains("passeggiata") })
    }

    @Test
    fun saveNotificationEpisode_marks_unread_and_recall_includes_it() = runTest {
        val unifiedDao = FakeMemoryDocumentDao()
        val unifiedRepository = UnifiedMemoryRepository.createForTest(unifiedDao)
        val activityRepository = ActivityLogRepository.createForTest(FakeActivityLogDao())
        val writer = UnifiedMemoryWriter(unifiedRepository, activityRepository, settingsRepository = null)

        writer.saveNotificationEpisode(
            appLabel = "WhatsApp",
            title = "Marco",
            text = "domani cinema",
            dedupKey = "wa:1",
            receivedAtMillis = System.currentTimeMillis(),
        )

        val unread = unifiedRepository.listUnreadNotificationEpisodes()
        assertEquals(1, unread.size)
        assertTrue(unread.single().isUnread)

        val recalled = unifiedRepository.recallForQuestion(
            MemoryRecallRequest(query = "messaggi Marco"),
        )
        assertTrue(recalled.any { it.externalRef == UnifiedMemoryRepository.notificationExternalRef("wa:1") })
    }

    @Test
    fun onReminderFired_deactivates_reminder_and_writes_episode() = runTest {
        val unifiedDao = FakeMemoryDocumentDao()
        val unifiedRepository = UnifiedMemoryRepository.createForTest(unifiedDao)
        val activityDao = FakeActivityLogDao()
        val activityRepository = ActivityLogRepository.createForTest(activityDao)
        val writer = UnifiedMemoryWriter(unifiedRepository, activityRepository, settingsRepository = null)

        unifiedRepository.saveReminderProjection(
            taskId = 9L,
            message = "Eseguire i test",
            triggerAtMillis = System.currentTimeMillis() + 60_000L,
        )

        writer.onReminderFired(
            taskId = 9L,
            message = "Eseguire i test",
            triggerAtMillis = System.currentTimeMillis(),
        )

        assertFalse(unifiedDao.getByExternalRef("reminder:9")!!.isActive)
        assertEquals(1, activityDao.countSince(0L))
        val episode = unifiedDao.getAllActive().firstOrNull { it.kind == MemoryDocumentKind.EPISODE.name }
        assertTrue(episode!!.value.contains("Eseguire i test"))
    }

    @Test
    fun onListItemAdded_writes_active_projection() = runTest {
        val unifiedDao = FakeMemoryDocumentDao()
        val unifiedRepository = UnifiedMemoryRepository.createForTest(unifiedDao)
        val activityRepository = ActivityLogRepository.createForTest(FakeActivityLogDao())
        val writer = UnifiedMemoryWriter(unifiedRepository, activityRepository, settingsRepository = null)

        writer.onListItemAdded(
            itemId = 2L,
            type = ListItemType.SHOPPING,
            text = "latte",
            checked = false,
        )

        val doc = unifiedDao.getByExternalRef("list_item:2")
        assertEquals("latte", doc!!.value)
        assertTrue(doc.isActive)
    }

    @Test
    fun markEpisodeRead_clears_unread_flag() = runTest {
        val unifiedDao = FakeMemoryDocumentDao()
        val unifiedRepository = UnifiedMemoryRepository.createForTest(unifiedDao)
        val activityRepository = ActivityLogRepository.createForTest(FakeActivityLogDao())
        val writer = UnifiedMemoryWriter(unifiedRepository, activityRepository, settingsRepository = null)

        val ref = UnifiedMemoryRepository.notificationExternalRef("dedup-1")
        writer.saveNotificationEpisode(
            appLabel = "Telegram",
            title = "Luca",
            text = "ciao",
            dedupKey = "dedup-1",
            receivedAtMillis = System.currentTimeMillis(),
        )
        assertEquals(1, unifiedRepository.listUnreadNotificationEpisodes().size)

        writer.markEpisodeRead(ref)
        assertTrue(unifiedRepository.listUnreadNotificationEpisodes().isEmpty())
        assertFalse(unifiedDao.getByExternalRef(ref)!!.isUnread)
    }
}
