package com.example.mydeskrobot.integration.tool.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSafetyTest {

    @Test
    fun `allows public https URL`() {
        val result = UrlSafety.validateHttpUrl("https://www.ansa.it/notizie")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `rejects localhost`() {
        val result = UrlSafety.validateHttpUrl("http://localhost/page")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `rejects file scheme`() {
        val result = UrlSafety.validateHttpUrl("file:///etc/passwd")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `rejects loopback IP`() {
        val result = UrlSafety.validateHttpUrl("http://127.0.0.1/admin")
        assertFalse(result.isSuccess)
    }
}
