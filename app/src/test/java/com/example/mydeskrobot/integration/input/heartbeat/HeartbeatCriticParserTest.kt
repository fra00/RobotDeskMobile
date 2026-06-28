package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.reasoning.model.CriticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartbeatCriticParserTest {

    @Test
    fun parse_approve() {
        val result = HeartbeatCriticParser.parse(
            """{"decision":"approve","reply":"","reason":"ok"}""",
            "Ciao, come va?",
        )
        assertTrue(result is CriticResult.Approve)
        assertEquals("Ciao, come va?", (result as CriticResult.Approve).text)
    }

    @Test
    fun parse_block() {
        val result = HeartbeatCriticParser.parse(
            """{"decision":"block","reply":"","reason":"repetition"}""",
            "Ripeto di nuovo",
        )
        assertEquals(CriticResult.Block, result)
    }

    @Test
    fun parse_modify() {
        val result = HeartbeatCriticParser.parse(
            """{"decision":"modify","reply":"Breve suggerimento.","reason":"shorten"}""",
            "Testo lungo",
        )
        assertTrue(result is CriticResult.Modify)
        assertEquals("Breve suggerimento.", (result as CriticResult.Modify).text)
    }
}
