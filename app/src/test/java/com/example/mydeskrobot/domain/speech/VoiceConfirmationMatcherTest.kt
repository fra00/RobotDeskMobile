package com.example.mydeskrobot.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceConfirmationMatcherTest {

    @Test
    fun `detects yes variants`() {
        assertEquals(VoiceConfirmationDecision.YES, VoiceConfirmationMatcher.parse("sì"))
        assertEquals(VoiceConfirmationDecision.YES, VoiceConfirmationMatcher.parse("procedi"))
        assertEquals(VoiceConfirmationDecision.YES, VoiceConfirmationMatcher.parse("ok vai"))
    }

    @Test
    fun `detects no variants`() {
        assertEquals(VoiceConfirmationDecision.NO, VoiceConfirmationMatcher.parse("no"))
        assertEquals(VoiceConfirmationDecision.NO, VoiceConfirmationMatcher.parse("annulla"))
    }

    @Test
    fun `long unrelated phrase is not confirmation`() {
        val phrase = "no voglio sapere che tempo fa domani a Roma per favore"
        assertEquals(VoiceConfirmationDecision.NOT_CONFIRMATION, VoiceConfirmationMatcher.parse(phrase))
    }
}
