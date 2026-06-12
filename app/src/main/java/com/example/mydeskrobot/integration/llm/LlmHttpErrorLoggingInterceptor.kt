package com.example.mydeskrobot.integration.llm

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Logs non-success HTTP response bodies (e.g. Gemini 429 JSON) without consuming the stream.
 */
class LlmHttpErrorLoggingInterceptor(
    private val logTag: String = TAG,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            val bodySnippet = runCatching {
                response.peekBody(MAX_PEEK_BYTES).string().trim()
            }.getOrElse { "failed to read body: ${it.message}" }
            Log.w(
                logTag,
                "HTTP ${response.code} ${request.method} ${request.url.encodedPath}: $bodySnippet",
            )
        }
        return response
    }

    companion object {
        private const val TAG = "LlmHttp"
        private const val MAX_PEEK_BYTES = 8_192L
    }
}
