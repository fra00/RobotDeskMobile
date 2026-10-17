package com.example.mydeskrobot.integration.proxy.capability

import com.example.mydeskrobot.integration.proxy.capability.handlers.PingCapabilityHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCapabilityRegistryTest {

    @Test
    fun `register and resolve ping by wire id`() = runBlocking {
        val registry = PhoneCapabilityRegistry()
        registry.register(PhoneCapabilityId.PING, PingCapabilityHandler())
        val handler = registry.getByWireId("ping")
        assertNotNull(handler)
        val json = handler!!.handle(emptyMap())
        assertTrue(json.optBoolean("pong"))
    }

    @Test
    fun `unknown wire id returns null`() {
        val registry = PhoneCapabilityRegistry(
            mapOf(PhoneCapabilityId.PING to PingCapabilityHandler()),
        )
        assertNull(registry.getByWireId("nope"))
        assertEquals(setOf(PhoneCapabilityId.PING), registry.knownIds())
    }

    @Test
    fun `fromWire is case insensitive`() {
        assertEquals(PhoneCapabilityId.EXERCISES, PhoneCapabilityId.fromWire("Exercises"))
        assertNull(PhoneCapabilityId.fromWire(" "))
    }
}
