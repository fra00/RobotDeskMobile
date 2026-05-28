package com.example.mydeskrobot.domain.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSpeechFilterTest {

    @Test
    fun detectsExactEcho() {
        val spoken = "Ciao, come posso aiutarti oggi?"
        assertTrue(EchoSpeechFilter.isLikelyAssistantEcho(spoken, spoken))
    }

    @Test
    fun detectsPartialEcho() {
        val spoken = "La temperatura fuori è di quindici gradi con cielo sereno"
        val heard = "temperatura fuori quindici gradi cielo sereno"
        assertTrue(EchoSpeechFilter.isLikelyAssistantEcho(heard, spoken))
    }

    @Test
    fun ignoresUnrelatedUserPhrase() {
        val spoken = "La temperatura fuori è di quindici gradi"
        val heard = "accendi la luce della cucina"
        assertFalse(EchoSpeechFilter.isLikelyAssistantEcho(heard, spoken))
    }

    @Test
    fun ignoresWhenNoPriorResponse() {
        assertFalse(EchoSpeechFilter.isLikelyAssistantEcho("ciao robot", null))
    }

    @Test
    fun stripLeadingEchoKeepsUserSuffix() {
        val spoken = "La temperatura fuori è di quindici gradi con cielo sereno"
        val heard = "La temperatura fuori è di quindici gradi con cielo sereno che ore sono"
        assertEquals(
            "che ore sono",
            EchoSpeechFilter.stripLeadingAssistantEcho(heard, spoken),
        )
    }
}
