package com.example.mydeskrobot.domain.mood

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnMoodEvaluatorTest {

    private val baseTime = 1_000_000_000L
    private val config = TurnMoodConfig(
        burstTurnCountThreshold = 4,
        burstWindowMs = 3 * 60_000L,
        repeatedPhraseThreshold = 3,
        shortPhraseWordLimit = 4,
        maxRecentSignatures = 8,
        positiveBoostCapPerWindow = 3,
    )

    @Test
    fun `voice turn adds presence delta trigger`() {
        val (_, signals) = TurnMoodEvaluator.evaluateUserTurn(
            phrase = "come stai oggi",
            session = ConversationMoodSession(),
            config = config,
            now = baseTime,
        )

        assertTrue(signals.triggers.any { it is MoodTrigger.VoiceTurnPresence })
    }

    @Test
    fun `burst of turns adds fatigue delta`() {
        var session = ConversationMoodSession()
        repeat(3) { i ->
            val result = TurnMoodEvaluator.evaluateUserTurn(
                phrase = "domanda $i",
                session = session,
                config = config,
                now = baseTime + i * 10_000L,
            )
            session = result.first
        }
        val (_, signals) = TurnMoodEvaluator.evaluateUserTurn(
            phrase = "domanda 4",
            session = session,
            config = config,
            now = baseTime + 40_000L,
        )

        assertTrue(
            signals.triggers.any {
                it is MoodTrigger.ValenceDelta && it.event == "fatigue_burst"
            },
        )
        assertTrue(signals.promptHints.any { it.contains("intensa") })
    }

    @Test
    fun `repeated phrase adds boredom delta`() {
        var session = ConversationMoodSession()
        repeat(2) {
            val result = TurnMoodEvaluator.evaluateUserTurn(
                phrase = "che ore sono",
                session = session,
                config = config,
                now = baseTime + it * 1000L,
            )
            session = result.first
        }
        val (_, signals) = TurnMoodEvaluator.evaluateUserTurn(
            phrase = "che ore sono",
            session = session,
            config = config,
            now = baseTime + 3000L,
        )

        assertTrue(
            signals.triggers.any {
                it is MoodTrigger.ValenceDelta && it.event == "frase_ripetuta"
            },
        )
    }

    @Test
    fun `routine happy from llm skips valence tier`() {
        val (_, signals) = TurnMoodEvaluator.evaluateLlmTurn(
            emotion = RobotEmotion.HAPPY,
            userTone = UserInteractionTone.NEUTRAL,
            session = ConversationMoodSession(),
            config = config,
            now = baseTime,
        )

        assertEquals(LlmEmotionValenceTier.ROUTINE, signals.llmEmotionValenceTier)
        assertEquals(0.5f, signals.ephemeralIntensityScale!!, 0.001f)
    }

    @Test
    fun `happy with positive user tone uses full valence tier`() {
        val (session, signals) = TurnMoodEvaluator.evaluateLlmTurn(
            emotion = RobotEmotion.HAPPY,
            userTone = UserInteractionTone.POSITIVE,
            session = ConversationMoodSession(),
            config = config,
            now = baseTime,
        )

        assertEquals(LlmEmotionValenceTier.FULL, signals.llmEmotionValenceTier)
        assertEquals(1, session.positiveBoostsInWindow)
    }

    @Test
    fun `happy after tool success stays routine tier`() {
        val session = ConversationMoodSession().onToolSuccess(baseTime - 1000L)
        val (_, signals) = TurnMoodEvaluator.evaluateLlmTurn(
            emotion = RobotEmotion.HAPPY,
            userTone = UserInteractionTone.NEUTRAL,
            session = session,
            config = config,
            now = baseTime,
        )

        assertEquals(LlmEmotionValenceTier.ROUTINE, signals.llmEmotionValenceTier)
        assertTrue(signals.promptHints.any { it.contains("post-tool") })
    }

    @Test
    fun `neutral llm emotion uses none tier`() {
        val (_, signals) = TurnMoodEvaluator.evaluateLlmTurn(
            emotion = RobotEmotion.NEUTRAL,
            userTone = UserInteractionTone.NEUTRAL,
            session = ConversationMoodSession(),
            config = config,
            now = baseTime,
        )

        assertEquals(LlmEmotionValenceTier.NONE, signals.llmEmotionValenceTier)
    }

    @Test
    fun `positive praise cap demotes happy to routine tier`() {
        var session = ConversationMoodSession()
        repeat(3) { i ->
            val result = TurnMoodEvaluator.evaluateLlmTurn(
                emotion = RobotEmotion.HAPPY,
                userTone = UserInteractionTone.POSITIVE,
                session = session,
                config = config,
                now = baseTime + i * 1000L,
            )
            session = result.first
        }
        val (_, signals) = TurnMoodEvaluator.evaluateLlmTurn(
            emotion = RobotEmotion.HAPPY,
            userTone = UserInteractionTone.POSITIVE,
            session = session,
            config = config,
            now = baseTime + 4000L,
        )

        assertEquals(LlmEmotionValenceTier.ROUTINE, signals.llmEmotionValenceTier)
        assertTrue(signals.promptHints.any { it.contains("Elogi frequenti") })
    }
}
