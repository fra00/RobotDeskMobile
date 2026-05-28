package com.example.mydeskrobot.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExitPhraseMatcherTest {

    private val matcher = ExitPhraseMatcher(exitPhrase = "stop robot")

    @Test
    fun `exit only on exact phrase`() {
        assertTrue(matcher.parse("stop robot") is ExitPhraseParseResult.ExitOnly)
    }

    @Test
    fun `content then exit when phrase at end`() {
        val result = matcher.parse("grazie stop robot")
        assertTrue(result is ExitPhraseParseResult.ContentThenExit)
        assertEquals("grazie", (result as ExitPhraseParseResult.ContentThenExit).content)
    }

    @Test
    fun `not exit on unrelated text`() {
        assertTrue(matcher.parse("fermati robot") is ExitPhraseParseResult.NotExit)
    }
}
