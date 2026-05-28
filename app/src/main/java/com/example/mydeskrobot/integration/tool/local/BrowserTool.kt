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
 * Browser tool for opening URLs.
 * Uses Intent.ACTION_VIEW to open URLs in the default browser.
 */
class BrowserTool(
    private val context: Context,
) : Tool {

    override val name: String = "open_browser"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Open a URL in the device's default browser. Accepts full URLs or domains like ansa.it",
            parameters = listOf(
                ToolParameter(
                    name = "url",
                    type = "string",
                    description = "URL or domain to open (e.g. https://www.ansa.it or ansa.it)",
                    required = true,
                )
            ),
            returns = "success (boolean)",
            example = """{"name": "open_browser", "params": {"url": "https://www.ansa.it"}, "await_result": false}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val rawUrl = invocation.params["url"]?.toString()?.trim()
            ?: return ToolResult.Error(
                message = "Parametro 'url' mancante",
                code = "MISSING_PARAM",
            )

        val url = normalizeUrl(rawUrl)
            ?: return ToolResult.Error(
                message = "URL non valido: $rawUrl",
                code = "INVALID_URL",
            )

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Do not gate on resolveActivity(): on API 30+ it returns null without <queries>
            // in the manifest even when browsers are installed.
            context.startActivity(intent)
            Log.i(TAG, "Browser aperto su: $url (raw=$rawUrl)")

            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "url" to url,
                )
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ActivityNotFoundException per URL: $url (raw=$rawUrl)", e)
            ToolResult.Error(
                message = "Nessun browser disponibile per aprire l'URL",
                code = "NO_BROWSER",
                recoverable = true,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore browser: ${e.message}", e)
            ToolResult.Error(
                message = "Impossibile aprire il browser: ${e.message}",
                code = "BROWSER_ERROR",
                recoverable = true,
            )
        }
    }

    internal companion object {
        private const val TAG = "BrowserTool"

        private val DOMAIN_PATTERN = Regex(
            """^(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}(?:/[^\s]*)?$""",
        )

        /**
         * Normalizes user/LLM input into a browser-ready URL.
         * - "https://www.ansa.it" → unchanged
         * - "ansa.it", "www.ansa.it" → prefixed with https://
         */
        fun normalizeUrl(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            val withoutScheme = trimmed
                .removePrefix("http://")
                .removePrefix("https://")
                .trimStart('/')

            if (withoutScheme.isEmpty()) return null

            val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else if (DOMAIN_PATTERN.matches(withoutScheme)) {
                "https://$withoutScheme"
            } else {
                return null
            }

            return runCatching { Uri.parse(candidate).toString() }.getOrNull()
        }
    }
}
