package com.example.mydeskrobot.domain.telephony

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberNormalizerTest {

    @Test
    fun normalize_italianMobileWithSpaces() {
        assertEquals("+393331234567", PhoneNumberNormalizer.normalize("+39 333 123 4567"))
    }

    @Test
    fun normalize_localDigits() {
        assertEquals("3331234567", PhoneNumberNormalizer.normalize("333 123 4567"))
    }

    @Test
    fun normalize_internationalPrefix00() {
        assertEquals("+393331234567", PhoneNumberNormalizer.normalize("0039 333 1234567"))
    }

    @Test
    fun normalize_tooShort_returnsNull() {
        assertNull(PhoneNumberNormalizer.normalize("12"))
    }

    @Test
    fun normalize_blank_returnsNull() {
        assertNull(PhoneNumberNormalizer.normalize("   "))
    }
}
