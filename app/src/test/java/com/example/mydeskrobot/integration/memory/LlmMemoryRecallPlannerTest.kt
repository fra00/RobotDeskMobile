package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.llm.LlmResponse
import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlanParser
import com.example.mydeskrobot.reasoning.memory.RecallFocus
import com.example.mydeskrobot.reasoning.memory.RecallPlanException
import com.example.mydeskrobot.reasoning.memory.RecallPlanFailure
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader

class LlmMemoryRecallPlannerTest {

    @Test
    fun plan_success_parses_valid_json() = runTest {
        val json = """
            {
              "temporal_scope": "NONE",
              "focus_day_key": null,
              "recall_focus": "USER_FACTS",
              "search_queries": ["professione sviluppatore web"],
              "include_habit_summary": false,
              "localize_spatial": false
            }
        """.trimIndent()
        val planner = plannerReturning(json)

        val result = planner.plan("che lavoro svolgo", System.currentTimeMillis())

        assertTrue(result.isSuccess)
        val plan = result.getOrThrow()
        assertEquals(RecallFocus.USER_FACTS, plan.recallFocus)
        assertEquals(listOf("professione sviluppatore web"), plan.searchQueries)
    }

    @Test
    fun plan_not_configured_returns_failure() = runTest {
        val planner = LlmMemoryRecallPlanner(
            llmClient = StubLlmClient(configured = false),
            systemPrompt = "planner",
        )

        val failure = planner.plan("che lavoro svolgo", System.currentTimeMillis()).exceptionOrNull()

        assertTrue(failure is RecallPlanException)
        assertTrue((failure as RecallPlanException).failure is RecallPlanFailure.NotConfigured)
    }

    @Test
    fun plan_llm_error_returns_failure() = runTest {
        val planner = LlmMemoryRecallPlanner(
            llmClient = StubLlmClient(
                response = Result.failure(IllegalStateException("network")),
            ),
            systemPrompt = "planner",
        )

        val failure = planner.plan("che lavoro svolgo", System.currentTimeMillis()).exceptionOrNull()

        assertTrue(failure is RecallPlanException)
        assertTrue((failure as RecallPlanException).failure is RecallPlanFailure.LlmError)
    }

    @Test
    fun plan_empty_output_returns_failure() = runTest {
        val planner = plannerReturning("   ")

        val failure = planner.plan("che lavoro svolgo", System.currentTimeMillis()).exceptionOrNull()

        assertTrue(failure is RecallPlanException)
        assertTrue((failure as RecallPlanException).failure is RecallPlanFailure.EmptyOutput)
    }

    @Test
    fun plan_invalid_json_returns_parse_failure() = runTest {
        val planner = plannerReturning("not json")

        val failure = planner.plan("che lavoro svolgo", System.currentTimeMillis()).exceptionOrNull()

        assertTrue(failure is RecallPlanException)
        assertTrue((failure as RecallPlanException).failure is RecallPlanFailure.ParseError)
    }

    @Test
    fun plan_blank_user_text_returns_parse_failure() = runTest {
        val planner = plannerReturning("{}")

        val failure = planner.plan("   ", System.currentTimeMillis()).exceptionOrNull()

        assertTrue(failure is RecallPlanException)
        assertTrue((failure as RecallPlanException).failure is RecallPlanFailure.ParseError)
    }

    @Test
    fun plan_passes_user_text_to_llm() = runTest {
        val stub = StubLlmClient(response = Result.success(LlmResponse(validUserFactsJson())))
        val planner = LlmMemoryRecallPlanner(stub, "system-prompt")

        planner.plan("che lavoro svolgo", System.currentTimeMillis())

        assertEquals("che lavoro svolgo", stub.lastUserMessage)
        assertEquals("system-prompt", stub.lastSystemPrompt)
    }

    @Test
    fun plan_skip_recall_succeeds_without_queries() = runTest {
        val planner = plannerReturning("""{"skip_recall": true}""")

        val result = planner.plan("allora buona notte", System.currentTimeMillis())

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().skipRecall)
    }

    @Test
    fun golden_fixture_buona_notte_skips_recall() {
        val raw = loadFixture("buona_notte")
        val plan = MemoryRecallPlanParser.parse(raw)

        assertTrue(plan!!.skipRecall)
    }

    @Test
    fun golden_fixtures_parse_to_expected_plans() {
        val cases = listOf(
            "che_lavoro_svolgo" to RecallFocus.USER_FACTS,
            "cosa_ho_fatto_ieri" to RecallFocus.EPISODIC,
            "chi_mi_ha_scritto" to RecallFocus.MESSAGES,
            "dimmi_orari_lavoro" to RecallFocus.USER_FACTS,
            "dove_siamo" to RecallFocus.SPATIAL,
            "tapis_roulant" to RecallFocus.EPISODIC,
            "cosa_devo_fare_domani" to RecallFocus.PLANNING,
            "motogp" to RecallFocus.USER_FACTS,
        )
        cases.forEach { (name, expectedFocus) ->
            val raw = loadFixture(name)
            val plan = MemoryRecallPlanParser.parse(raw)
            assertTrue("fixture $name should parse", plan != null)
            assertEquals("fixture $name focus", expectedFocus, plan!!.recallFocus)
            assertTrue("fixture $name search_queries", plan.searchQueries.isNotEmpty())
        }
    }

    @Test
    fun golden_fixture_week_includes_habit_summary_flag() {
        val raw = loadFixture("cosa_ho_fatto_questa_settimana")
        val plan = MemoryRecallPlanParser.parse(raw)

        assertEquals(TemporalScope.WEEK, plan!!.temporalScope)
        assertTrue(plan.includeHabitSummary)
    }

    private fun plannerReturning(content: String): LlmMemoryRecallPlanner =
        LlmMemoryRecallPlanner(
            llmClient = StubLlmClient(response = Result.success(LlmResponse(content))),
            systemPrompt = "planner",
        )

    private fun validUserFactsJson(): String = """
        {
          "temporal_scope": "NONE",
          "focus_day_key": null,
          "recall_focus": "USER_FACTS",
          "search_queries": ["professione"],
          "include_habit_summary": false,
          "localize_spatial": false
        }
    """.trimIndent()

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("recall_planner/$name.json")
            ?: error("Missing fixture recall_planner/$name.json")
        return stream.bufferedReader().use(BufferedReader::readText)
    }

    private class StubLlmClient(
        private val response: Result<LlmResponse> = Result.success(LlmResponse("")),
        private val configured: Boolean = true,
    ) : LlmClient {
        var lastSystemPrompt: String? = null
        var lastUserMessage: String? = null

        override suspend fun chat(
            messages: List<ConversationMessage>,
            systemPrompt: String,
        ): Result<LlmResponse> {
            lastSystemPrompt = systemPrompt
            lastUserMessage = (messages.lastOrNull() as? ConversationMessage.User)?.content
            return response
        }

        override suspend fun chatWithImage(
            messages: List<ConversationMessage>,
            systemPrompt: String,
            imageBytes: ByteArray,
        ): Result<LlmResponse> = Result.failure(UnsupportedOperationException())

        override fun isConfigured(): Boolean = configured
    }
}
