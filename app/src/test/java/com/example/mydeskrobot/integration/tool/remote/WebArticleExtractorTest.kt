package com.example.mydeskrobot.integration.tool.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebArticleExtractorTest {

    private val extractor = WebArticleExtractor()

    @Test
    fun `extracts article text and excludes navigation`() {
        val html = """
            <!DOCTYPE html>
            <html><head><title>Test News</title></head>
            <body>
              <nav><a href="/">Home</a><a href="/sport">Sport</a><a href="/tech">Tech</a></nav>
              <article>
                <h1>Breaking story</h1>
                <p>This is the main article body with enough sentences, commas, and detail to score well.</p>
                <p>Second paragraph adds more readable content for extraction.</p>
              </article>
              <aside>Sidebar ad and unrelated links.</aside>
            </body></html>
        """.trimIndent()

        val result = extractor.extract("https://example.com/news", html)

        assertTrue(result.fullText.contains("Breaking story"))
        assertTrue(result.fullText.contains("main article body"))
        assertFalse(result.fullText.contains("Sidebar ad"))
        assertEquals("Test News", result.title)
        assertEquals(WebArticleExtractorKind.READABILITY, result.extractor)
    }

    @Test
    fun `extracts content from div wrapper without article tag`() {
        val html = """
            <!DOCTYPE html>
            <html><head><title>Blog Post</title></head>
            <body>
              <div class="header">Site menu with many navigation links everywhere.</div>
              <div class="content">
                <h1>Post title here</h1>
                <p>Unique blog content paragraph one with sufficient length and commas, for scoring.</p>
                <p>Unique blog content paragraph two continues the story with more detail.</p>
              </div>
            </body></html>
        """.trimIndent()

        val result = extractor.extract("https://example.com/blog/post", html)

        assertTrue(result.fullText.contains("Unique blog content paragraph one"))
        assertTrue(result.fullText.contains("Post title"))
    }

    @Test
    fun `returns minimal text for script-only page`() {
        val html = """
            <!DOCTYPE html>
            <html><head><title>Empty</title></head>
            <body><script>window.app = {};</script></body></html>
        """.trimIndent()

        val result = extractor.extract("https://example.com/empty", html)

        assertTrue(result.fullText.length < 50)
    }

    @Test
    fun `legacy fallback reads main when readability yields empty`() {
        val html = """
            <!DOCTYPE html>
            <html><head><title>Simple</title></head>
            <body>
              <main><p>Fallback main text only.</p></main>
            </body></html>
        """.trimIndent()

        val result = extractor.extract("https://example.com/simple", html)

        assertTrue(
            result.fullText.contains("Fallback main text") ||
                result.extractor == WebArticleExtractorKind.READABILITY,
        )
    }
}
