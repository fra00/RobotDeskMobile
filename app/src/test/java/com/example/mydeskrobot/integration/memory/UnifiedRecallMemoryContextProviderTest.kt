package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.MemoryRecallRequest
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.MemoryContextOptions
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlan
import com.example.mydeskrobot.reasoning.memory.RecallFocus
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UnifiedRecallMemoryContextProviderTest {

    @Test
    fun query_includes_semantically_relevant_fact() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                userFact(1L, "L'utente ha un cane di nome Brina"),
            ),
        )
        val provider = providerFor(dao)

        val context = buildWithPlan(
            provider = provider,
            userText = "Come si chiama il mio cane?",
            plan = MemoryRecallPlan(
                recallFocus = RecallFocus.USER_FACTS,
                searchQueries = listOf("cane nome Brina", "animale domestico"),
            ),
        )

        assertTrue(context.contains("MEMORIA"))
        assertTrue(context.contains("Brina"))
    }

    @Test
    fun yesterday_query_includes_all_episodes_for_day_not_only_voice_physical() = runTest {
        val yesterday = yesterdayDayKey()
        val dao = FakeMemoryDocumentDao(
            listOf(
                episode(1L, "Lavoro dalle 13", EpisodeKind.PHYSICAL_NOW, yesterday, source = MemoryDocumentSource.TOOL),
                episode(2L, "Messaggio da Marco", EpisodeKind.SOCIAL_THREAD, yesterday, source = MemoryDocumentSource.EXTRACTOR),
                episode(3L, "Passeggiata", EpisodeKind.PHYSICAL_NOW, dayBefore(yesterday)),
            ),
        )
        val provider = providerFor(dao)

        val context = buildWithPlan(
            provider = provider,
            userText = "cosa ho fatto ieri",
            plan = MemoryRecallPlan(
                temporalScope = TemporalScope.SINGLE_DAY,
                focusDayKey = yesterday,
                recallFocus = RecallFocus.EPISODIC,
                searchQueries = listOf("attività ieri", "cosa ho fatto"),
            ),
        )

        assertTrue(context.contains("Lavoro dalle 13"))
        assertTrue(context.contains("Messaggio da Marco"))
        assertFalse(context.contains("Passeggiata"))
    }

    @Test
    fun empty_recall_returns_blank_context() = runTest {
        val provider = providerFor(FakeMemoryDocumentDao())

        val context = buildWithPlan(
            provider = provider,
            userText = "chi era Garibaldi",
            plan = MemoryRecallPlan(
                recallFocus = RecallFocus.GENERAL,
                searchQueries = listOf("Garibaldi storia"),
            ),
        )

        assertEquals("", context)
    }

    @Test
    fun null_plan_returns_blank_context() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(userFact(1L, "L'utente ha un cane di nome Brina")),
        )
        val provider = providerFor(dao)

        val context = provider.buildContextFor(
            userText = "Come si chiama il mio cane?",
            recallPlan = null,
        )

        assertEquals("", context)
    }

    @Test
    fun buildContextFor_increments_use_count() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                userFact(1L, "L'utente ha un cane di nome Brina"),
            ),
        )
        val repository = UnifiedMemoryRepository.createForTest(dao)
        val provider = UnifiedRecallMemoryContextProvider(repository)

        buildWithPlan(
            provider = provider,
            userText = "Come si chiama il mio cane?",
            plan = MemoryRecallPlan(
                recallFocus = RecallFocus.USER_FACTS,
                searchQueries = listOf("cane nome Brina"),
            ),
        )

        assertEquals(1, dao.getById(1L)!!.useCount)
    }

    @Test
    fun recallForQuestion_scope_linked_episodes_bypass_low_semantic_score() = runTest {
        val yesterday = "2026-06-18"
        val dao = FakeMemoryDocumentDao(
            listOf(
                episode(1L, "Evento automatico extractor", EpisodeKind.PLAN, yesterday),
            ),
        )
        val repository = UnifiedMemoryRepository.createForTest(dao)

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "cosa ho fatto ieri",
                focusDayKey = yesterday,
                searchQueries = listOf("cosa ho fatto ieri"),
            ),
        )

        assertEquals(1, recalled.size)
        assertTrue(recalled.first().value.contains("Evento automatico"))
    }

    @Test
    fun freshVisionVerify_excludes_spatial_from_recall() = runTest {
        val dao = FakeMemoryDocumentDao(
            listOf(
                MemoryDocumentEntity(
                    id = 1L,
                    value = "studio: scrivania, quadro, finestra",
                    kind = MemoryDocumentKind.SPATIAL.name,
                    category = "study",
                    source = MemoryDocumentSource.TOOL.name,
                    confidence = 1f,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
                userFact(2L, "L'utente ha un cane di nome Brina"),
            ),
        )
        val repository = UnifiedMemoryRepository.createForTest(dao)

        val recalled = repository.recallForQuestion(
            MemoryRecallRequest(
                query = "dove siamo",
                localizeQuery = true,
                excludeSpatialLandmarks = true,
                includeVisionCatalog = true,
                searchQueries = listOf("persone animali oggetti stanza"),
            ),
        )

        assertTrue(recalled.none { it.kind == MemoryDocumentKind.SPATIAL.name })
    }

    @Test
    fun week_query_includes_habit_summary_when_planner_requests_it() = runTest {
        val dao = FakeMemoryDocumentDao()
        val repository = UnifiedMemoryRepository.createForTest(dao)
        repository.saveHabitSummaryProjection(
            summaryText = "Di solito pranza verso le 13",
            sourceEventCount = 8,
        )
        val provider = UnifiedRecallMemoryContextProvider(repository)

        val context = buildWithPlan(
            provider = provider,
            userText = "cosa ho fatto questa settimana",
            plan = MemoryRecallPlan(
                temporalScope = TemporalScope.WEEK,
                recallFocus = RecallFocus.GENERAL,
                searchQueries = listOf("attività settimana", "episodi recenti"),
                includeHabitSummary = true,
            ),
        )

        assertTrue(context.contains("PROFILO ABITUDINI"))
        assertTrue(context.contains("questa settimana"))
    }

    private suspend fun buildWithPlan(
        provider: UnifiedRecallMemoryContextProvider,
        userText: String,
        plan: MemoryRecallPlan,
    ): String = provider.buildContextFor(
        userText = userText,
        recallPlan = plan,
        profileOverride = null,
        options = MemoryContextOptions(),
    )

    private fun providerFor(dao: FakeMemoryDocumentDao): UnifiedRecallMemoryContextProvider {
        val repository = UnifiedMemoryRepository.createForTest(dao)
        return UnifiedRecallMemoryContextProvider(repository)
    }

    private fun userFact(id: Long, value: String) = MemoryDocumentEntity(
        id = id,
        value = value,
        kind = MemoryDocumentKind.USER_FACT.name,
        category = MemoryCategory.FACT.name,
        source = MemoryDocumentSource.TOOL.name,
        confidence = 0.9f,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun episode(
        id: Long,
        value: String,
        kind: EpisodeKind,
        dayKey: String,
        source: MemoryDocumentSource = MemoryDocumentSource.EXTRACTOR,
    ) = MemoryDocumentEntity(
        id = id,
        value = value,
        kind = MemoryDocumentKind.EPISODE.name,
        category = kind.name,
        source = source.name,
        confidence = 1f,
        createdAt = 1L,
        updatedAt = 1L,
        dayKey = dayKey,
        scheduledDayKey = dayKey,
    )

    private fun yesterdayDayKey(): String {
        val calendar = Calendar.getInstance(Locale.ITALY)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return dayKeyFormat.format(calendar.time)
    }

    private fun dayBefore(dayKey: String): String {
        val calendar = Calendar.getInstance(Locale.ITALY)
        calendar.time = dayKeyFormat.parse(dayKey)!!
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        return dayKeyFormat.format(calendar.time)
    }

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
}
