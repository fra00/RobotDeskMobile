package com.example.mydeskrobot.domain.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppUriBuilderTest {

    @Test
    fun buildSendUri_encodesMessage() {
        val uri = WhatsAppUriBuilder.buildSendUri("393331234567", "ciao mondo")
        assertTrue(uri.toString().contains("phone=393331234567"))
        assertTrue(uri.toString().contains("text=ciao+mondo"))
    }

    @Test
    fun normalizeSendId_stripsJidSuffix() {
        assertEquals(
            "120363016464847264",
            WhatsAppUriBuilder.normalizeSendId("120363016464847264@g.us"),
        )
    }

    @Test
    fun normalizeSendId_stripsPhonePrefix() {
        assertEquals("393331234567", WhatsAppUriBuilder.normalizeSendId("+39 333 1234567"))
    }
}
