package com.example.mydeskrobot.data.llm

import okhttp3.Interceptor
import okhttp3.Response

/** LM Studio: API key opzionale; se assente non invia Authorization. */
class LlmAuthInterceptor(
    private val apiKey: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        if (apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        return chain.proceed(requestBuilder.build())
    }
}
