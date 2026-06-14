package com.example.mydeskrobot.domain.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNameMatcherTest {

    @Test
    fun `mamma matches madre display name`() {
        val score = ContactNameMatcher.score("Madre Rossi", "mamma")
        assertTrue(score >= 0.85f)
    }

    @Test
    fun `marco matches marco bianchi`() {
        val score = ContactNameMatcher.score("Marco Bianchi", "Marco")
        assertTrue(score >= 0.85f)
    }

    @Test
    fun `unrelated name scores low`() {
        val score = ContactNameMatcher.score("Luca Verdi", "Marco")
        assertTrue(score < 0.55f)
    }

    @Test
    fun `expandTerms includes madre for mamma`() {
        val terms = ContactNameAliases.expandTerms("mamma")
        assertTrue("madre" in terms)
        assertEquals("mamma", ContactNameAliases.normalize("Mamma"))
    }
}
