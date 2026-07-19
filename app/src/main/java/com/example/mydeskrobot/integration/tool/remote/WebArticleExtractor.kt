package com.example.mydeskrobot.integration.tool.remote

import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Extracts main article text from HTML using Readability4J with a legacy Jsoup fallback.
 */
class WebArticleExtractor {

    fun extract(url: String, html: String): WebArticleExtraction {
        val readability = runCatching {
            val article = Readability4JExtended(url, html).parse()
            val text = normalizeWhitespace(article.textContent.orEmpty())
            if (text.isBlank()) return@runCatching null
            WebArticleExtraction(
                title = article.title?.trim().orEmpty(),
                fullText = text,
                excerpt = article.excerpt?.trim()?.takeIf { it.isNotBlank() },
                extractor = WebArticleExtractorKind.READABILITY,
            )
        }.getOrNull()

        if (readability != null) {
            return readability
        }

        val doc = Jsoup.parse(html, url)
        val legacyText = extractTextLegacy(doc)
        return WebArticleExtraction(
            title = doc.title().trim(),
            fullText = legacyText,
            excerpt = null,
            extractor = WebArticleExtractorKind.LEGACY,
        )
    }

    private fun extractTextLegacy(doc: Document): String {
        doc.select("script, style, nav, footer, header, aside, noscript").remove()
        val main = doc.selectFirst("article, main, [role=main]")
        val source = main ?: doc.body()
        return normalizeWhitespace(source?.text().orEmpty())
    }

    private fun normalizeWhitespace(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}

enum class WebArticleExtractorKind(val wireName: String) {
    READABILITY("readability"),
    LEGACY("legacy"),
}

data class WebArticleExtraction(
    val title: String,
    val fullText: String,
    val excerpt: String?,
    val extractor: WebArticleExtractorKind,
)
