package com.example.mydeskrobot.integration.proxy.capability

import com.example.mydeskrobot.integration.proxy.capability.handlers.ContactsCapabilityHandler
import com.example.mydeskrobot.integration.telephony.ContactPhoneLookup
import com.example.mydeskrobot.integration.telephony.ContactPhoneMatch
import com.example.mydeskrobot.integration.telephony.ContactPhoneSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsCapabilityHandlerTest {

    private class FakeLookup : ContactPhoneLookup {
        override fun hasPermission(): Boolean = true
        override fun search(query: String): List<ContactPhoneMatch> = listOf(
            ContactPhoneMatch("Anna", "+391", ContactPhoneSource.CONTACTS, 0.9f),
        )
    }

    @Test
    fun `returns matches json`() = runBlocking {
        val json = ContactsCapabilityHandler(FakeLookup()).handle(mapOf("q" to "Anna"))
        assertTrue(json.optBoolean("permission"))
        assertEquals(1, json.getJSONArray("matches").length())
        assertEquals("Anna", json.getJSONArray("matches").getJSONObject(0).getString("displayName"))
    }

    @Test(expected = PhoneCapabilityException::class)
    fun `empty q throws`(): Unit = runBlocking {
        ContactsCapabilityHandler(FakeLookup()).handle(mapOf("q" to "  "))
        Unit
    }
}
