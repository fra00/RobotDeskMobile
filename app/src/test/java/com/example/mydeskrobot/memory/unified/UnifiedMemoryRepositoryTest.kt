package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlan
import com.example.mydeskrobot.reasoning.memory.RecallFocus
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedMemoryRepositoryTest {

    @Test
    fun searchRelevant_finds_paraphrase_via_token_hybrid() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "Il venerdì lavora dalle 9:00 alle 13:00",
            category = MemoryCategory.ROUTINE,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val results = repository.searchRelevant(
            query = "quando lavoro il venerdì",
            limit = 5,
            filters = MemoryDocumentFilters(kinds = setOf(MemoryDocumentKind.USER_FACT)),
        )

        assertEquals(1, results.size)
        assertTrue(results.first().value.contains("venerdì"))
    }

    @Test
    fun searchRelevant_returns_empty_when_below_min_score() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "Il cane si chiama Brina",
            category = MemoryCategory.FACT,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val results = repository.searchRelevant(
            query = "chi era Garibaldi",
            limit = 5,
            minScore = 0.45f,
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun deactivateReminderProjection_marks_inactive() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveReminderProjection(
            taskId = 42L,
            message = "Prendi le medicine",
            triggerAtMillis = System.currentTimeMillis() + 60_000L,
        )

        repository.deactivateReminderProjection(42L)

        val active = dao.getAllActive()
        assertTrue(active.isEmpty())
        val stored = dao.getByExternalRef("reminder:42")
        assertFalse(stored!!.isActive)
    }

    @Test
    fun markUsed_increments_use_count() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        val id = repository.saveUserFact(
            value = "L'utente si chiama Francesco",
            category = MemoryCategory.IDENTITY,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )
        val doc = dao.getById(id)!!

        repository.markUsed(listOf(doc))

        assertEquals(1, dao.getById(id)!!.useCount)
    }

    @Test
    fun saveListItemProjection_deactivates_checked_todo() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveListItemProjection(
            itemId = 7L,
            type = com.example.mydeskrobot.domain.list.ListItemType.TODO,
            text = "Comprare latte",
            checked = true,
            source = MemoryDocumentSource.TOOL,
        )

        val active = dao.getAllActive()
        assertTrue(active.isEmpty())
    }

    @Test
    fun saveEpisodeProjection_includes_raw_phrase_in_value() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveEpisodeProjection(
            eventId = 7L,
            label = "messaggio da Mario",
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.SOCIAL_THREAD,
            dayKey = "2026-06-18",
            timestampMs = 1_000L,
            actor = "Mario",
            sourceChannel = "WhatsApp",
            rawPhrase = "il cielo era blu",
            source = MemoryDocumentSource.EXTRACTOR,
        )

        val doc = dao.getByExternalRef(UnifiedMemoryRepository.activityLogExternalRef(7L))
        assertEquals("messaggio da Mario — \"il cielo era blu\"", doc!!.value)
        assertEquals("Mario", doc.actor)
    }

    @Test
    fun saveEpisodeProjection_stores_scheduled_day() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveEpisodeProjection(
            eventId = 11L,
            label = "cinema",
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PLAN,
            dayKey = "2026-06-19",
            timestampMs = 1_000L,
            scheduledDayKey = "2026-06-20",
            scheduledAtMs = 1_700_000_000_000L,
            source = MemoryDocumentSource.TOOL,
        )

        val doc = dao.getByExternalRef("activity_log:11")
        assertEquals("cinema", doc!!.value)
        assertEquals("2026-06-20", doc.scheduledDayKey)
        assertEquals(MemoryDocumentKind.EPISODE.name, doc.kind)
    }

    @Test
    fun saveSpatialPlaceProjection_stores_landmarks() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveSpatialPlaceProjection(
            placeId = 3L,
            label = "studio",
            landmarks = listOf("scrivania", "monitor"),
            roomType = "study",
            description = "ufficio",
            source = MemoryDocumentSource.TOOL,
        )

        val doc = dao.getByExternalRef("spatial_place:3")
        assertTrue(doc!!.value.contains("studio"))
        assertTrue(doc.value.contains("scrivania"))
    }

    @Test
    fun saveHabitSummaryProjection_stores_summary_with_ttl() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveHabitSummaryProjection(
            summaryText = "Colazione verso le 8, pausa pranzo verso le 13",
            sourceEventCount = 12,
            source = MemoryDocumentSource.EXTRACTOR,
        )

        val doc = dao.getByExternalRef(UnifiedMemoryRepository.HABIT_SUMMARY_EXTERNAL_REF)
        assertEquals("Colazione verso le 8, pausa pranzo verso le 13", doc!!.value)
        assertEquals(MemoryDocumentKind.HABIT_SUMMARY.name, doc.kind)
        assertEquals("12", doc.category)
        assertTrue(doc.expiresAt != null)
    }

    @Test
    fun recallForQuestion_preserves_user_facts_when_many_episodes_for_day() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repeat(50) { index ->
            repository.saveEpisodeProjection(
                eventId = index.toLong() + 1L,
                label = "attività $index",
                eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PHYSICAL_NOW,
                dayKey = "2026-06-18",
                timestampMs = 1_000L + index,
                source = MemoryDocumentSource.TOOL,
            )
        }
        val brinaFacts = listOf(
            "Il cane Brina ha tre anni e ama correre nel giardino",
            "Brina è un meticcio marrone che dorme sul divano blu",
            "La ciotola dell'acqua di Brina sta in cucina vicino al frigo",
            "Brina va dal veterinario ogni primavera per i richiami",
            "Il guinzaglio rosso di Brina è appeso all'ingresso",
        )
        brinaFacts.forEach { fact ->
            repository.upsertUserFacingFact(
                category = MemoryCategory.FACT,
                value = fact,
                confidence = 0.9f,
                source = MemoryDocumentSource.TOOL,
            )
        }

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "cosa sai del cane Brina",
                temporalScope = TemporalScope.SINGLE_DAY,
                focusDayKey = "2026-06-18",
            ),
        )

        val userFacts = recalled.count { it.kind == MemoryDocumentKind.USER_FACT.name }
        assertTrue(userFacts >= 3)
        assertTrue(recalled.size <= MemoryRecallBudget.TOTAL)
    }

    @Test
    fun recallForQuestion_preferEpisodicDetail_includes_tapis_not_habit_summary() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveHabitSummaryProjection(
            summaryText = "Di solito passeggia con il cane Brina la mattina",
            sourceEventCount = 5,
            source = MemoryDocumentSource.EXTRACTOR,
        )
        val threeDaysAgo = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        val dayKey = com.example.mydeskrobot.data.activitylog.ActivityLogRepository.dayKeyFor(threeDaysAgo)
        repository.saveEpisodeProjection(
            eventId = 16L,
            label = "tapis roulant",
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PHYSICAL_NOW,
            dayKey = dayKey,
            timestampMs = threeDaysAgo,
            rawPhrase = "40 minuti",
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "tapis roulant recentemente",
                temporalScope = TemporalScope.WEEK,
                preferEpisodicDetail = true,
            ),
        )

        assertTrue(recalled.any { it.value.contains("tapis roulant", ignoreCase = true) })
        assertTrue(recalled.none { it.kind == MemoryDocumentKind.HABIT_SUMMARY.name })
    }

    @Test
    fun recallForQuestion_general_week_includes_recent_episodes() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "L'utente si chiama Francesco",
            category = MemoryCategory.IDENTITY,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )
        val twoDaysAgo = System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000
        val dayKey = com.example.mydeskrobot.data.activitylog.ActivityLogRepository.dayKeyFor(twoDaysAgo)
        repository.saveEpisodeProjection(
            eventId = 42L,
            label = "pranzo con Marco",
            eventKind = com.example.mydeskrobot.domain.activitylog.EpisodeKind.PHYSICAL_NOW,
            dayKey = dayKey,
            timestampMs = twoDaysAgo,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallPlan(
                recallFocus = RecallFocus.GENERAL,
                searchQueries = listOf("fatti episodi promemoria"),
            ).toRequest("ripeti tutto quello che sai di me"),
        )

        assertTrue(recalled.any { it.kind == MemoryDocumentKind.EPISODE.name })
        assertTrue(recalled.any { it.kind == MemoryDocumentKind.USER_FACT.name })
    }

    @Test
    fun recallForQuestion_preferUserFacts_returns_work_hours_not_habit_summary() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveHabitSummaryProjection(
            summaryText = "Di solito colazione verso le 8 e pausa pranzo verso le 13",
            sourceEventCount = 8,
            source = MemoryDocumentSource.EXTRACTOR,
        )
        repository.saveUserFact(
            value = "Lunedì lavora dalle 9:00 alle 13:00 e dalle 14:00 alle 18:00",
            category = MemoryCategory.ROUTINE,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )
        repository.saveUserFact(
            value = "Venerdì lavora dalle 9:00 alle 13:00",
            category = MemoryCategory.ROUTINE,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "dimmi gli orari di lavoro",
                preferUserFacts = true,
                searchQueries = listOf("orari lavoro lunedì venerdì", "routine orario ufficio"),
            ),
        )

        assertTrue(recalled.any { it.value.contains("Lunedì") })
        assertTrue(recalled.any { it.value.contains("Venerdì") })
        assertTrue(recalled.none { it.kind == MemoryDocumentKind.HABIT_SUMMARY.name })
    }

    @Test
    fun recallForQuestion_preferUserFacts_returns_sviluppatore_web() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "L'utente è sviluppatore web",
            category = MemoryCategory.IDENTITY,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "che lavoro svolgo",
                preferUserFacts = true,
                searchQueries = listOf("professione sviluppatore web", "che lavoro svolge"),
            ),
        )

        assertTrue(recalled.any { it.value.contains("sviluppatore web", ignoreCase = true) })
    }

    @Test
    fun recallForQuestion_preferUserFacts_returns_motogp() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "Guarda la MotoGP",
            category = MemoryCategory.PREFERENCE,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "che motorsport seguo",
                preferUserFacts = true,
                searchQueries = listOf("motogp motorsport preferenza", "sport seguito"),
            ),
        )

        assertTrue(recalled.any { it.value.contains("MotoGP", ignoreCase = true) })
    }

    @Test
    fun recallForQuestion_preferUserFacts_returns_name_with_planner_only_queries() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "L'utente si chiama Francesco",
            category = MemoryCategory.IDENTITY,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "come mi chiamo",
                preferUserFacts = true,
                searchQueries = listOf("nome identità utente"),
            ),
        )

        assertTrue(recalled.any { it.value.contains("Francesco", ignoreCase = true) })
    }

    @Test
    fun recallForQuestion_preferUserFacts_pins_identity_even_when_search_misses() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "Ti chiami Francesco",
            category = MemoryCategory.IDENTITY,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )
        repository.saveUserFact(
            value = "Guarda la MotoGP",
            category = MemoryCategory.PREFERENCE,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "come mi chiamo",
                preferUserFacts = true,
                searchQueries = listOf("xyz unrelated topic"),
            ),
        )

        assertTrue(recalled.any { it.value.contains("Francesco", ignoreCase = true) })
    }

    @Test
    fun recallForQuestion_preferUserFacts_returns_ti_chiami_phrasing() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveUserFact(
            value = "Ti chiami Francesco",
            category = MemoryCategory.IDENTITY,
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
        )

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "Quale è il mio nome",
                preferUserFacts = true,
                searchQueries = listOf("nome identità"),
            ),
        )

        assertTrue(recalled.any { it.value.contains("Francesco", ignoreCase = true) })
    }

    @Test
    fun pruneIfNeeded_keeps_isPinned_facts() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.upsertUserFacingFact(
            category = MemoryCategory.FACT,
            value = "L'utente è allergico alle noci",
            confidence = 0.95f,
            source = MemoryDocumentSource.TOOL,
            isPinned = true,
        )
        val now = System.currentTimeMillis()
        repeat(301) { index ->
            dao.upsert(
                MemoryDocumentEntity(
                    value = "Voce archivio uid ${(index + 10_000).toString(36)}",
                    kind = MemoryDocumentKind.USER_FACT.name,
                    category = MemoryCategory.FACT.name,
                    source = MemoryDocumentSource.TOOL.name,
                    confidence = 0.5f,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        assertEquals(302, dao.countActive())

        val pruned = repository.pruneIfNeeded(maxItems = 300)
        assertTrue(pruned > 0)
        val allergy = dao.getAllActive().firstOrNull { it.value.contains("allergico") }
        assertTrue(allergy != null)
        assertTrue(allergy!!.isPinned)
    }
}
