package com.example.mydeskrobot.domain.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberExtractorTest {

    @Test
    fun `extracts number from memory style text`() {
        val number = PhoneNumberExtractor.extractFirst("Madre: +39 333 1234567")
        assertEquals("+393331234567", number)
    }

    @Test
    fun `returns null when no number`() {
        assertNull(PhoneNumberExtractor.extractFirst("Mi piace la pizza"))
    }
}
