package com.example.mydeskrobot.integration.vision

import com.example.mydeskrobot.domain.vision.PresenceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PresenceResponseParserTest {

    @Test
    fun parse_present() {
        val result = PresenceResponseParser.parse("""{"presence": "present", "confidence": 0.92}""")
        assertEquals(PresenceStatus.PRESENT, result?.status)
        assertEquals(0.92f, result?.confidence)
    }

    @Test
    fun parse_absent() {
        val result = PresenceResponseParser.parse("""{"presence": "absent", "confidence": 0.88}""")
        assertEquals(PresenceStatus.ABSENT, result?.status)
    }

    @Test
    fun parse_uncertain() {
        val result = PresenceResponseParser.parse("""{"presence": "uncertain", "confidence": 0.4}""")
        assertEquals(PresenceStatus.UNCERTAIN, result?.status)
    }

    @Test
    fun parse_extractsJsonFromProse() {
        val raw = """Here is the result: {"presence": "present", "confidence": 0.7} done."""
        val result = PresenceResponseParser.parse(raw)
        assertEquals(PresenceStatus.PRESENT, result?.status)
    }

    @Test
    fun parse_invalidLabelReturnsNull() {
        assertNull(PresenceResponseParser.parse("""{"presence": "maybe", "confidence": 0.5}"""))
    }

    @Test
    fun parse_clampsConfidence() {
        val result = PresenceResponseParser.parse("""{"presence": "absent", "confidence": 1.5}""")
        assertEquals(1f, result?.confidence)
    }

    @Test
    fun parse_defaultConfidenceWhenMissing() {
        val result = PresenceResponseParser.parse("""{"presence": "absent"}""")
        assertEquals(0.5f, result?.confidence)
    }
}
