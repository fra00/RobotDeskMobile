package com.example.mydeskrobot.integration.tool.local

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.mydeskrobot.domain.messaging.WhatsAppUriBuilder
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Opens WhatsApp with a pre-filled message. User taps Send — does not auto-send.
 */
class SendWhatsAppTool(
    private val context: Context,
) : Tool {

    override val name: String = "send_whatsapp"
    override val locality: ToolLocality = ToolLocality.LOCAL

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Open WhatsApp chat with message pre-filled. User taps Send. " +
                "Use after resolve_whatsapp_target when target is known, or pass send_id/number directly.",
            parameters = listOf(
                ToolParameter(
                    name = "message",
                    type = "string",
                    description = "Message text to pre-fill",
                    required = true,
                ),
                ToolParameter(
                    name = "send_id",
                    type = "string",
                    description = "Phone digits or WhatsApp group id from resolve_whatsapp_target",
                    required = false,
                ),
                ToolParameter(
                    name = "number",
                    type = "string",
                    description = "Alias for send_id when sending to a contact number",
                    required = false,
                ),
            ),
            returns = "success, message, send_id, whatsapp_opened",
            example = """{"name": "send_whatsapp", "params": {"send_id": "120363016464847264", "message": "Ciao!"}, "await_result": false}""",
        )
    }

    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val message = invocation.params["message"]?.toString()?.trim().orEmpty()
        if (message.isBlank()) {
            return ToolResult.Error(
                message = "Parametro 'message' mancante",
                code = "MISSING_PARAM",
            )
        }

        val rawSendId = invocation.params["send_id"]?.toString()?.trim()
            .orEmpty()
            .ifBlank { invocation.params["number"]?.toString()?.trim().orEmpty() }
        if (rawSendId.isBlank()) {
            return ToolResult.Error(
                message = "Parametro 'send_id' o 'number' mancante",
                code = "MISSING_PARAM",
            )
        }

        val sendId = WhatsAppUriBuilder.normalizeSendId(rawSendId)
        if (sendId.length < 5) {
            return ToolResult.Error(
                message = "Destinatario WhatsApp non valido: $rawSendId",
                code = "INVALID_TARGET",
            )
        }

        val httpsUri = WhatsAppUriBuilder.buildSendUri(sendId, message)
        return try {
            openWhatsApp(httpsUri)
            Log.i(TAG, "WhatsApp opened sendId=$sendId")
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "message" to message,
                    "send_id" to sendId,
                    "whatsapp_opened" to true,
                    "user_action_required" to "Tap Send in WhatsApp to deliver the message",
                ),
            )
        } catch (e: ActivityNotFoundException) {
            tryFallbackScheme(sendId, message)
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp open error: ${e.message}", e)
            ToolResult.Error(
                message = "Impossibile aprire WhatsApp: ${e.message}",
                code = "WHATSAPP_ERROR",
                recoverable = true,
            )
        }
    }

    private fun openWhatsApp(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(WHATSAPP_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun tryFallbackScheme(sendId: String, message: String): ToolResult {
        return try {
            val uri = WhatsAppUriBuilder.buildFallbackSchemeUri(sendId, message)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success(
                data = mapOf(
                    "success" to true,
                    "message" to message,
                    "send_id" to sendId,
                    "whatsapp_opened" to true,
                    "user_action_required" to "Tap Send in WhatsApp to deliver the message",
                ),
            )
        } catch (e: ActivityNotFoundException) {
            ToolResult.Error(
                message = "WhatsApp non installato",
                code = "NO_WHATSAPP",
                recoverable = true,
            )
        }
    }

    companion object {
        private const val TAG = "SendWhatsAppTool"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
    }
}
