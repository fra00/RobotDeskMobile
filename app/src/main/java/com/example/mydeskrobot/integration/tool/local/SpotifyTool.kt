package com.example.mydeskrobot.integration.tool.local

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Opens Spotify with a search query via deep link (Level 1 — no API/OAuth).
 * Playback may require the user to tap Play in the Spotify app.
 */
class SpotifyTool(
    private val context: Context,
) : Tool {

    override val name: String = "play_spotify"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Open Spotify and search for music, artist, or genre. " +
                "Use for requests like play music, listen to an artist, or a genre.",
            parameters = listOf(
                ToolParameter(
                    name = "query",
                    type = "string",
                    description = "Search text (artist, song, genre). Use 'musica' for generic music requests.",
                    required = false,
                ),
            ),
            returns = "success (boolean), query (string)",
            example = """{"name": "play_spotify", "params": {"query": "Nirvana"}, "await_result": false}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val rawQuery = invocation.params["query"]?.toString().orEmpty()
        val searchQuery = normalizeQuery(rawQuery)
        val httpsUri = buildSearchUri(searchQuery)

        return try {
            openUri(httpsUri)
            Log.i(TAG, "Spotify aperto: query=$searchQuery uri=$httpsUri")
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "query" to searchQuery,
                ),
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Spotify non disponibile per query=$searchQuery", e)
            tryFallbackSpotifyScheme(searchQuery)
        } catch (e: Exception) {
            Log.e(TAG, "Errore Spotify: ${e.message}", e)
            ToolResult.Error(
                message = "Impossibile aprire Spotify: ${e.message}",
                code = "SPOTIFY_ERROR",
                recoverable = true,
            )
        }
    }

    private fun openUri(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun tryFallbackSpotifyScheme(query: String): ToolResult {
        val fallbackUri = buildSpotifySchemeUri(query)
        return try {
            openUri(fallbackUri)
            Log.i(TAG, "Spotify aperto (fallback scheme): query=$query")
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "query" to query,
                    "fallback" to true,
                ),
            )
        } catch (e: ActivityNotFoundException) {
            ToolResult.Error(
                message = "Spotify non è installato sul dispositivo",
                code = "NO_SPOTIFY",
                recoverable = true,
            )
        }
    }

    internal companion object {
        private const val TAG = "SpotifyTool"
        private const val DEFAULT_QUERY = "musica"
        private const val HTTPS_SEARCH_BASE = "https://open.spotify.com/search/"

        fun normalizeQuery(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return DEFAULT_QUERY
            return trimmed
        }

        fun buildSearchUri(query: String): Uri {
            val normalized = normalizeQuery(query)
            val encoded = Uri.encode(normalized)
            return Uri.parse("$HTTPS_SEARCH_BASE$encoded")
        }

        fun buildSpotifySchemeUri(query: String): Uri {
            val normalized = normalizeQuery(query)
            val encoded = Uri.encode(normalized)
            return Uri.parse("spotify:search:$encoded")
        }
    }
}
