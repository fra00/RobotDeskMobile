package com.example.mydeskrobot.reasoning.memory

import com.example.mydeskrobot.reasoning.MemoryContextOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRecallPlanParserTest {

    @Test
    fun parse_valid_user_facts_plan() {
        val raw = """
            {
              "temporal_scope": "NONE",
              "focus_day_key": null,
              "recall_focus": "USER_FACTS",
              "search_queries": ["professione sviluppatore web", "che lavoro svolge"],
              "include_habit_summary": false,
              "localize_spatial": false
            }
        """.trimIndent()

        val plan = MemoryRecallPlanParser.parse(raw)

        assertEquals(TemporalScope.NONE, plan!!.temporalScope)
        assertNull(plan.focusDayKey)
        assertEquals(RecallFocus.USER_FACTS, plan.recallFocus)
        assertEquals(2, plan.searchQueries.size)
        assertFalse(plan.includeHabitSummary)
        assertFalse(plan.localizeSpatial)
    }

    @Test
    fun parse_valid_single_day_episodic() {
        val raw = """
            {
              "temporal_scope": "SINGLE_DAY",
              "focus_day_key": "2026-06-18",
              "recall_focus": "EPISODIC",
              "search_queries": ["attività fisica ieri", "cosa ho fatto"],
              "include_habit_summary": false,
              "localize_spatial": false
            }
        """.trimIndent()

        val plan = MemoryRecallPlanParser.parse(raw)

        assertEquals(TemporalScope.SINGLE_DAY, plan!!.temporalScope)
        assertEquals("2026-06-18", plan.focusDayKey)
        assertEquals(RecallFocus.EPISODIC, plan.recallFocus)
    }

    @Test
    fun parse_strips_code_fence() {
        val raw = """
            ```json
            {
              "temporal_scope": "WEEK",
              "focus_day_key": null,
              "recall_focus": "MESSAGES",
              "search_queries": ["messaggi whatsapp ricevuti"],
              "include_habit_summary": false,
              "localize_spatial": false
            }
            ```
        """.trimIndent()

        val plan = MemoryRecallPlanParser.parse(raw)

        assertEquals(TemporalScope.WEEK, plan!!.temporalScope)
        assertEquals(RecallFocus.MESSAGES, plan.recallFocus)
    }

    @Test
    fun parse_rejects_invalid_day_key() {
        val raw = """
            {
              "temporal_scope": "SINGLE_DAY",
              "focus_day_key": "ieri",
              "recall_focus": "EPISODIC",
              "search_queries": ["attività"],
              "include_habit_summary": false,
              "localize_spatial": false
            }
        """.trimIndent()

        assertNull(MemoryRecallPlanParser.parse(raw))
    }

    @Test
    fun parse_rejects_empty_search_queries() {
        val raw = """
            {
              "temporal_scope": "NONE",
              "focus_day_key": null,
              "recall_focus": "GENERAL",
              "search_queries": [],
              "include_habit_summary": false,
              "localize_spatial": false
            }
        """.trimIndent()

        assertNull(MemoryRecallPlanParser.parse(raw))
    }

    @Test
    fun parse_skip_recall_without_search_queries() {
        val raw = """{"skip_recall": true}"""

        val plan = MemoryRecallPlanParser.parse(raw)

        assertTrue(plan!!.skipRecall)
        assertTrue(plan.searchQueries.isEmpty())
    }

    @Test
    fun parse_skip_recall_ignores_empty_search_queries() {
        val raw = """
            {
              "skip_recall": true,
              "search_queries": []
            }
        """.trimIndent()

        val plan = MemoryRecallPlanParser.parse(raw)

        assertTrue(plan!!.skipRecall)
    }
}

class MemoryRecallPlanMappingTest {

    @Test
    fun user_facts_maps_to_preferUserFacts() {
        val request = MemoryRecallPlan(
            recallFocus = RecallFocus.USER_FACTS,
            searchQueries = listOf("orari lavoro routine"),
        ).toRequest("che lavoro svolgo")

        assertTrue(request.preferUserFacts)
        assertFalse(request.preferEpisodicDetail)
        assertEquals(listOf("orari lavoro routine"), request.searchQueries)
    }

    @Test
    fun episodic_maps_to_preferEpisodicDetail() {
        val request = MemoryRecallPlan(
            temporalScope = TemporalScope.SINGLE_DAY,
            focusDayKey = "2026-06-18",
            recallFocus = RecallFocus.EPISODIC,
            searchQueries = listOf("tapis roulant allenamento"),
        ).toRequest("tapis roulant")

        assertTrue(request.preferEpisodicDetail)
        assertEquals("2026-06-18", request.focusDayKey)
    }

    @Test
    fun general_expands_none_scope_to_week() {
        val request = MemoryRecallPlan(
            recallFocus = RecallFocus.GENERAL,
            searchQueries = listOf("fatti episodi promemoria"),
        ).toRequest("ripeti tutto quello che sai di me")

        assertEquals(TemporalScope.WEEK, request.temporalScope)
        assertFalse(request.preferUserFacts)
        assertTrue(request.includeHabitSummary)
    }

    @Test
    fun messages_expands_none_scope_to_week() {
        val request = MemoryRecallPlan(
            recallFocus = RecallFocus.MESSAGES,
            searchQueries = listOf("chi ha scritto whatsapp"),
        ).toRequest("chi mi ha scritto")

        assertEquals(TemporalScope.WEEK, request.temporalScope)
    }

    @Test
    fun spatial_maps_localizeQuery() {
        val request = MemoryRecallPlan(
            recallFocus = RecallFocus.SPATIAL,
            localizeSpatial = true,
            searchQueries = listOf("stanza corrente studio"),
        ).toRequest("dove siamo")

        assertTrue(request.localizeQuery)
    }

    @Test
    fun vision_catalog_sets_includeVisionCatalog() {
        val request = MemoryRecallPlan.visionCatalog().toRequest(
            userText = "",
            options = MemoryContextOptions(freshVisionVerify = true),
        )

        assertTrue(request.includeVisionCatalog)
        assertTrue(request.excludeSpatialLandmarks)
    }

    @Test
    fun habit_summary_flag_passed_through() {
        val request = MemoryRecallPlan(
            temporalScope = TemporalScope.WEEK,
            recallFocus = RecallFocus.GENERAL,
            searchQueries = listOf("abitudini settimanali"),
            includeHabitSummary = true,
        ).toRequest("cosa faccio di solito")

        assertTrue(request.includeHabitSummary)
    }
}
