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
            description = "Open a URL in the device's default browser",
            parameters = listOf(
                ToolParameter(
                    name = "url",
                    type = "string",
                    description = "URL to open (must start with http:// or https://)",
                    required = true,
                )
            ),
            returns = "success (boolean)",
            example = """{"name": "open_browser", "params": {"url": "https://www.example.com"}, "await_result": false}""",
        )
    }
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val url = invocation.params["url"]?.toString()
            ?: return ToolResult.Error(
                message = "Parametro 'url' mancante",
                code = "MISSING_PARAM",
            )
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult.Error(
                message = "URL non valido: deve iniziare con http:// o https://",
                code = "INVALID_URL",
            )
        }
        
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (intent.resolveActivity(context.packageManager) == null) {
                Log.w(TAG, "Nessuna activity disponibile per URL: $url")
                return ToolResult.Error(
                    message = "Nessun browser disponibile per aprire l'URL",
                    code = "NO_BROWSER",
                    recoverable = true,
                )
            }
            
            context.startActivity(intent)
            Log.i(TAG, "Browser aperto su: $url")
            
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "url" to url,
                )
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ActivityNotFoundException per URL: $url", e)
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
    
    companion object {
        private const val TAG = "BrowserTool"
    }
}
