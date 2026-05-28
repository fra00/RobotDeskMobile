package com.example.mydeskrobot.integration.tool.remote

import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Weather tool using OpenWeatherMap API.
 * Provides current weather for a given city.
 */
class WeatherTool(
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : Tool {
    
    override val name: String = "get_weather"
    override val locality: ToolLocality = ToolLocality.REMOTE
    
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(WeatherResponse::class.java)
    
    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Get current weather for a city",
            parameters = listOf(
                ToolParameter(
                    name = "city",
                    type = "string",
                    description = "City name (e.g., 'Roma', 'Milano')",
                    required = true,
                )
            ),
            returns = "temperature (int), condition (string), humidity (int), wind_speed (float)",
            example = """{"name": "get_weather", "params": {"city": "Roma"}, "await_result": true}""",
        )
    }
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val city = invocation.params["city"]?.toString()
            ?: return ToolResult.Error(
                message = "Parametro 'city' mancante",
                code = "MISSING_PARAM",
            )
        
        if (apiKey.isBlank()) {
            return ToolResult.Error(
                message = "API key meteo non configurata",
                code = "NOT_CONFIGURED",
                recoverable = false,
            )
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(city)
                val request = Request.Builder().url(url).get().build()
                
                val response = httpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext ToolResult.Error(
                        message = "Errore API meteo: ${response.code}",
                        code = "API_ERROR",
                    )
                }
                
                val body = response.body?.string()
                    ?: return@withContext ToolResult.Error(
                        message = "Risposta vuota dall'API meteo",
                        code = "EMPTY_RESPONSE",
                    )
                
                val weather = adapter.fromJson(body)
                    ?: return@withContext ToolResult.Error(
                        message = "Impossibile parsare risposta meteo",
                        code = "PARSE_ERROR",
                    )
                
                ToolResult.Success(
                    data = mapOf(
                        "city" to (weather.name ?: city),
                        "temperature" to (weather.main?.temp?.toInt() ?: 0),
                        "condition" to (weather.weather?.firstOrNull()?.description ?: "sconosciuto"),
                        "humidity" to (weather.main?.humidity ?: 0),
                        "wind_speed" to (weather.wind?.speed ?: 0.0),
                    )
                )
            } catch (e: Exception) {
                ToolResult.Error(
                    message = "Errore durante la richiesta meteo: ${e.message}",
                    code = "NETWORK_ERROR",
                )
            }
        }
    }
    
    private fun buildUrl(city: String): String {
        val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
        return "$BASE_URL?q=$encodedCity&appid=$apiKey&units=metric&lang=it"
    }
    
    companion object {
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
        
        private fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}

internal data class WeatherResponse(
    val name: String? = null,
    val main: MainData? = null,
    val weather: List<WeatherData>? = null,
    val wind: WindData? = null,
)

internal data class MainData(
    val temp: Double? = null,
    val humidity: Int? = null,
)

internal data class WeatherData(
    val description: String? = null,
    @Json(name = "main")
    val main: String? = null,
)

internal data class WindData(
    val speed: Double? = null,
)
