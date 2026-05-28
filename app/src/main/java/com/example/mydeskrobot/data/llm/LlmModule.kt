package com.example.mydeskrobot.data.llm

import com.example.mydeskrobot.BuildConfig
import com.example.mydeskrobot.domain.repository.LlmRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object LlmModule {

    fun createRepository(systemPrompt: String): LlmRepository {
        val api = createOpenAiApi(
            baseUrl = BuildConfig.LLM_BASE_URL,
            apiKey = BuildConfig.LLM_API_KEY,
        )
        val visionModel = BuildConfig.LLM_VISION_MODEL.trim().ifBlank { BuildConfig.LLM_MODEL }

        return LlmRepositoryImpl(
            api = api,
            textModel = BuildConfig.LLM_MODEL,
            visionModel = visionModel,
            systemPrompt = systemPrompt,
        )
    }

    /**
     * Creates a configured OpenAI-compatible Retrofit API.
     * Exposed for the integration layer (LmStudioClient).
     */
    fun createOpenAiApi(
        baseUrl: String = BuildConfig.LLM_BASE_URL,
        apiKey: String = BuildConfig.LLM_API_KEY,
    ): OpenAiChatApi {
        val normalizedUrl = baseUrl.let { url ->
            if (url.endsWith("/")) url else "$url/"
        }

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val client = OkHttpClient.Builder()
            .addInterceptor(LlmAuthInterceptor(apiKey = apiKey))
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(OpenAiChatApi::class.java)
    }
}
