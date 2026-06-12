package com.example.mydeskrobot.integration.body

import com.example.mydeskrobot.data.body.BodySettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class BodyApiResult<out T> {
    data class Success<T>(val data: T) : BodyApiResult<T>()
    data class Error(val message: String, val httpCode: Int? = null) : BodyApiResult<Nothing>()
}

class BodyApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = defaultClient(),
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val statusAdapter = moshi.adapter(BodyStatusJson::class.java)
    private val okAdapter = moshi.adapter(BodyOkResponse::class.java)
    private val jsonMediaType = "application/json".toMediaType()

    suspend fun getStatus(): BodyApiResult<BodyStatus> = withContext(Dispatchers.IO) {
        request("GET", "status") { body ->
            val parsed = statusAdapter.fromJson(body)
                ?: return@request BodyApiResult.Error("Risposta status non valida")
            BodyApiResult.Success(parsed.toDomain())
        }
    }

    suspend fun moveJoint(
        joint: BodyJoint,
        delta: Int? = null,
        position: Int? = null,
        speed: Int? = null,
    ): BodyApiResult<BodyOkResponse> = withContext(Dispatchers.IO) {
        if (delta == null && position == null) {
            return@withContext BodyApiResult.Error("Serve delta o position")
        }
        val json = BodyRequestBuilder.moveJointJson(delta = delta, position = position, speed = speed)
        request("POST", "joint/${joint.apiName}", json) { body -> parseOk(body) }
    }

    suspend fun moveJoints(
        jointPositions: Map<BodyJoint, Int>,
        speed: Int? = null,
    ): BodyApiResult<BodyOkResponse> = withContext(Dispatchers.IO) {
        if (jointPositions.isEmpty()) {
            return@withContext BodyApiResult.Error("Nessun joint specificato")
        }
        val json = BodyRequestBuilder.moveJointsJson(jointPositions, speed)
        request("POST", "joints", json) { body -> parseOk(body) }
    }

    suspend fun home(speed: Int? = null): BodyApiResult<BodyOkResponse> = withContext(Dispatchers.IO) {
        val json = BodyRequestBuilder.homeJson(speed)
        request("POST", "home", json) { body -> parseOk(body) }
    }

    suspend fun runTest(speed: Int = 40): BodyApiResult<BodyOkResponse> = withContext(Dispatchers.IO) {
        val json = BodyRequestBuilder.testJson(speed.coerceIn(1, 100))
        request("POST", "test", json) { body -> parseOk(body) }
    }

    private inline fun <T> request(
        method: String,
        path: String,
        jsonBody: String? = null,
        parse: (String) -> BodyApiResult<T>,
    ): BodyApiResult<T> {
        if (baseUrl.isBlank()) return BodyApiResult.Error("URL corpo non configurato")
        val builder = Request.Builder().url(BodyUrl.join(baseUrl, path))
        when (method) {
            "GET" -> builder.get()
            "POST" -> {
                val body = (jsonBody ?: "{}").toRequestBody(jsonMediaType)
                builder.post(body)
            }
            else -> return BodyApiResult.Error("Metodo non supportato: $method")
        }
        return try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = parseErrorBody(responseBody) ?: "HTTP ${response.code}"
                    return BodyApiResult.Error(err, response.code)
                }
                parse(responseBody)
            }
        } catch (e: Exception) {
            BodyApiResult.Error(e.message ?: "Errore di rete")
        }
    }

    private fun parseOk(body: String): BodyApiResult<BodyOkResponse> {
        val parsed = okAdapter.fromJson(body)
            ?: return BodyApiResult.Error("Risposta non valida")
        if (parsed.error != null) {
            return BodyApiResult.Error(parsed.error)
        }
        if (parsed.ok != true) {
            return BodyApiResult.Error("Operazione non confermata")
        }
        return BodyApiResult.Success(parsed)
    }

    private fun parseErrorBody(body: String): String? {
        if (body.isBlank()) return null
        return okAdapter.fromJson(body)?.error
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()

        fun createIfConfigured(settings: BodySettings): BodyApiClient? {
            if (!settings.isConfigured()) return null
            return BodyApiClient(settings.baseUrl)
        }
    }
}
