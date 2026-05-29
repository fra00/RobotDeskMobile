package com.example.mydeskrobot.integration.tool.remote

import android.util.Log
import com.example.mydeskrobot.data.search.SearchSettingsRepository
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * SearXNG JSON API: GET {base}/search?q=...&format=json&language=it
 */
class SearxngWebSearchEngine(
    private val baseUrlsProvider: suspend () -> List<String>,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : WebSearchEngine {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(SearxngResponse::class.java)

    override suspend fun search(query: String, maxResults: Int): Result<List<WebSearchHit>> {
        val baseUrls = baseUrlsProvider().distinct().filter { it.isNotBlank() }
        if (baseUrls.isEmpty()) {
            return Result.failure(IllegalStateException("URL SearXNG non configurato"))
        }

        var lastError: Throwable? = null
        for (baseUrl in baseUrls) {
            val attempt = searchOnInstance(baseUrl, query, maxResults)
            if (attempt.isSuccess) return attempt
            lastError = attempt.exceptionOrNull()
            Log.w(TAG, "SearXNG $baseUrl failed: ${lastError?.message}")
        }
        return Result.failure(
            lastError ?: IllegalStateException("Tutte le istanze SearXNG non disponibili"),
        )
    }

    private suspend fun searchOnInstance(
        baseUrl: String,
        query: String,
        maxResults: Int,
    ): Result<List<WebSearchHit>> {
        val baseHttpUrl = baseUrl.toHttpUrlOrNull()
            ?: return Result.failure(IllegalArgumentException("URL SearXNG non valido: $baseUrl"))

        val limit = maxResults.coerceIn(1, MAX_RESULTS)
        val httpUrl = baseHttpUrl.newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("language", "it")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(httpUrl)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("SearXNG HTTP ${response.code} ($baseUrl)"),
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(
                        IllegalStateException("Risposta SearXNG vuota ($baseUrl)"),
                    )

                val parsed = adapter.fromJson(body)
                    ?: return@withContext Result.failure(
                        IllegalStateException("JSON SearXNG non valido ($baseUrl)"),
                    )

                val hits = parsed.results
                    .orEmpty()
                    .asSequence()
                    .filter { !it.url.isNullOrBlank() }
                    .take(limit)
                    .map { row ->
                        WebSearchHit(
                            title = row.title?.trim().orEmpty().ifBlank { row.url!! },
                            url = row.url!!.trim(),
                            snippet = row.content?.trim().orEmpty(),
                        )
                    }
                    .toList()

                if (hits.isEmpty()) {
                    Result.failure(IllegalStateException("Nessun risultato SearXNG ($baseUrl)"))
                } else {
                    Result.success(hits)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        private const val TAG = "SearxngSearch"
        private const val MAX_RESULTS = 10
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun create(settings: SearchSettingsRepository): SearxngWebSearchEngine {
            return SearxngWebSearchEngine(baseUrlsProvider = { settings.getSearxBaseUrls() })
        }

        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}

internal data class SearxngResponse(
    val results: List<SearxngResult>? = null,
)

internal data class SearxngResult(
    val url: String? = null,
    val title: String? = null,
    val content: String? = null,
    @Json(name = "engine")
    val engine: String? = null,
)
