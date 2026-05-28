package com.example.mydeskrobot.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {

    private val matcher = WakePhraseMatcher(wakePhrase = "ehi robot")

    @Test
    fun `accepts transcript with wake phrase and query`() {
        val result = matcher.parse("ehi robot che tempo fa oggi")

        assertTrue(result is WakePhraseParseResult.Accepted)
        val accepted = result as WakePhraseParseResult.Accepted
        assertEquals("che tempo fa oggi", accepted.query)
    }

    @Test
    fun `accepts transcript case insensitive`() {
        val result = matcher.parse("Ehi Robot, quanto fa due più due")

        assertTrue(result is WakePhraseParseResult.Accepted)
        assertEquals("quanto fa due più due", (result as WakePhraseParseResult.Accepted).query)
    }

    @Test
    fun `rejects transcript without wake phrase`() {
        val result = matcher.parse("che tempo fa oggi")

        assertTrue(result is WakePhraseParseResult.Rejected)
        assertEquals(
            WakePhraseParseResult.RejectReason.MISSING_WAKE_PHRASE,
            (result as WakePhraseParseResult.Rejected).reason,
        )
    }

    @Test
    fun `rejects wake phrase only without query`() {
        val result = matcher.parse("ehi robot")

        assertTrue(result is WakePhraseParseResult.Rejected)
        assertEquals(
            WakePhraseParseResult.RejectReason.EMPTY_QUERY_AFTER_WAKE_PHRASE,
            (result as WakePhraseParseResult.Rejected).reason,
        )
    }

    @Test
    fun `extractQueryOrFull returns query when wake phrase repeated`() {
        assertEquals("che tempo fa", matcher.extractQueryOrFull("ehi robot che tempo fa"))
    }

    @Test
    fun `extractQueryOrFull returns full text when wake phrase omitted`() {
        assertEquals("che tempo fa", matcher.extractQueryOrFull("che tempo fa"))
    }
}
