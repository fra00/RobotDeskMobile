package com.example.mydeskrobot.integration.llm

import com.example.mydeskrobot.data.llm.LlmModule
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.integration.llm.gemini.GeminiApi
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object LlmClientFactory {

    private const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

    fun create(settings: LlmSettings): LlmClient {
        return when (settings.provider) {
            LlmProvider.LM_STUDIO -> {
                val api = LlmModule.createOpenAiApi(
                    baseUrl = settings.baseUrl,
                    apiKey = settings.apiKey,
                )
                LmStudioClient(
                    api = api,
                    textModel = settings.textModel,
                    visionModel = settings.resolvedVisionModel(),
                )
            }
            LlmProvider.GEMINI -> {
                val api = createGeminiApi()
                GeminiClient(
                    api = api,
                    apiKey = settings.apiKey,
                    textModel = settings.textModel,
                    visionModel = settings.resolvedVisionModel(),
                )
            }
        }
    }

    private fun createGeminiApi(): GeminiApi {
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(GeminiApi::class.java)
    }
}
