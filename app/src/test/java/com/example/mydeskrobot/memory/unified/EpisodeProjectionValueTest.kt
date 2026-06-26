package com.example.mydeskrobot.memory.unified

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeProjectionValueTest {

    @Test
    fun format_includes_raw_phrase_when_present() {
        val value = EpisodeProjectionValue.format(
            label = "messaggio da Mario",
            rawPhrase = "il cielo era blu",
        )
        assertEquals("messaggio da Mario — \"il cielo era blu\"", value)
    }

    @Test
    fun format_skips_duplicate_phrase() {
        val value = EpisodeProjectionValue.format(
            label = "il cielo era blu",
            rawPhrase = "il cielo era blu",
        )
        assertEquals("il cielo era blu", value)
    }
}
