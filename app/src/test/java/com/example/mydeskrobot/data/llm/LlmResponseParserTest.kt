package com.example.mydeskrobot.data.llm

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmResponseParserTest {

    private val parser = LlmResponseParser()

    @Test
    fun parsesJsonWithEmotion() {
        val raw = """{"reply":"Grazie mille!","emotion":"happy"}"""
        val reply = parser.parse(raw)
        assertEquals("Grazie mille!", reply.text)
        assertEquals(RobotEmotion.HAPPY, reply.emotion)
    }

    @Test
    fun parsesJsonWithoutEmotion_keepsNullEmotion() {
        val raw = """{"reply":"Va bene."}"""
        val reply = parser.parse(raw)
        assertEquals("Va bene.", reply.text)
        assertNull(reply.emotion)
    }

    @Test
    fun parsesJsonInCodeFence() {
        val raw = """
            ```json
            {"reply":"Mi dispiace.","emotion":"sad"}
            ```
        """.trimIndent()
        val reply = parser.parse(raw)
        assertEquals("Mi dispiace.", reply.text)
        assertEquals(RobotEmotion.SAD, reply.emotion)
    }

    @Test
    fun plainTextFallback() {
        val raw = "Ciao, come posso aiutarti?"
        val reply = parser.parse(raw)
        assertEquals(raw, reply.text)
        assertNull(reply.emotion)
    }

    @Test
    fun acceptsTextFieldAlias() {
        val raw = """{"text":"Risposta breve","emotion":"neutral"}"""
        val reply = parser.parse(raw)
        assertEquals("Risposta breve", reply.text)
        assertEquals(RobotEmotion.NEUTRAL, reply.emotion)
    }

    @Test
    fun parsesImageRequiredFlag() {
        val raw = """{"reply":"Ok, guardo.","emotion":"neutral","imageRequired":true}"""
        val reply = parser.parse(raw)
        assertEquals("Ok, guardo.", reply.text)
        assertTrue(reply.imageRequired)
    }

    @Test
    fun imageRequiredWithoutReplyText() {
        val raw = """{"imageRequired":true}"""
        val reply = parser.parse(raw)
        assertEquals("", reply.text)
        assertTrue(reply.imageRequired)
    }
}
