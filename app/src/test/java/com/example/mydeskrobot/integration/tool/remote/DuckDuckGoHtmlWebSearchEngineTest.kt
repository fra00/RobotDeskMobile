package com.example.mydeskrobot.integration.tool.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuckDuckGoHtmlWebSearchEngineTest {

    private val engine = DuckDuckGoHtmlWebSearchEngine()

    @Test
    fun `parseResults extracts title url snippet`() {
        val html = """
            <div class="result">
              <a class="result__a" href="https://it.wikipedia.org/wiki/Bosone">Bosone - Wikipedia</a>
              <div class="result__snippet">Particle in physics.</div>
            </div>
        """.trimIndent()

        val hits = engine.parseResults(html, 3)
        assertEquals(1, hits.size)
        assertEquals("Bosone - Wikipedia", hits[0].title)
        assertEquals("https://it.wikipedia.org/wiki/Bosone", hits[0].url)
        assertTrue(hits[0].snippet.contains("Particle"))
    }

    @Test
    fun `normalizeResultUrl decodes uddg redirect`() {
        val url = engine.normalizeResultUrl(
            "https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage&rut=abc",
        )
        assertEquals("https://example.com/page", url)
    }
}
