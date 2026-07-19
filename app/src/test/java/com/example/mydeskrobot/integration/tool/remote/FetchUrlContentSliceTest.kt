package com.example.mydeskrobot.integration.tool.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchUrlContentSliceTest {

    @Test
    fun `parseMaxChars defaults and coerces to hard cap`() {
        assertEquals(FetchUrlContentSlice.DEFAULT_MAX_CHARS, FetchUrlContentSlice.parseMaxChars(null))
        assertEquals(4500, FetchUrlContentSlice.parseMaxChars(9000))
        assertEquals(200, FetchUrlContentSlice.parseMaxChars(50))
    }

    @Test
    fun `parseStartChar defaults and rejects negative`() {
        assertEquals(0, FetchUrlContentSlice.parseStartChar(null))
        assertEquals(0, FetchUrlContentSlice.parseStartChar(-10))
        assertEquals(100, FetchUrlContentSlice.parseStartChar(100))
    }

    @Test
    fun `slice returns window with metadata`() {
        val fullText = "a".repeat(10_000)
        val result = FetchUrlContentSlice.slice(fullText, startChar = 100, maxChars = 4000)

        assertEquals(4000, result.charsReturned)
        assertEquals(10_000, result.charsTotal)
        assertEquals(100, result.startChar)
        assertTrue(result.truncated)
        assertEquals(4000, result.content.length)
    }

    @Test
    fun `slice respects hard cap when max_chars exceeds limit`() {
        val fullText = "b".repeat(20_000)
        val maxChars = FetchUrlContentSlice.parseMaxChars(8000)
        val result = FetchUrlContentSlice.slice(fullText, startChar = 0, maxChars = maxChars)

        assertEquals(FetchUrlContentSlice.MAX_LLM_CONTENT_CHARS, result.charsReturned)
    }

    @Test
    fun `slice not truncated when text fits`() {
        val fullText = "Short article text."
        val result = FetchUrlContentSlice.slice(fullText, startChar = 0, maxChars = 4000)

        assertFalse(result.truncated)
        assertEquals(fullText.length, result.charsReturned)
        assertEquals(fullText, result.content)
    }

    @Test
    fun `slice at end returns empty content`() {
        val fullText = "hello"
        val result = FetchUrlContentSlice.slice(fullText, startChar = 5, maxChars = 100)

        assertEquals("", result.content)
        assertEquals(5, result.charsTotal)
        assertFalse(result.truncated)
    }
}
