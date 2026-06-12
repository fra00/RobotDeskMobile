package com.example.mydeskrobot.integration.body

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyUrlTest {

    @Test
    fun `normalize adds http prefix`() {
        assertEquals("http://192.168.1.42", BodyUrl.normalize("192.168.1.42"))
    }

    @Test
    fun `normalize strips trailing slash`() {
        assertEquals("http://192.168.1.42", BodyUrl.normalize("http://192.168.1.42/"))
    }

    @Test
    fun `join builds path`() {
        assertEquals(
            "http://192.168.1.42/status",
            BodyUrl.join("http://192.168.1.42", "status"),
        )
    }
}
