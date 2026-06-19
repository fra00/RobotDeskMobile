package com.example.mydeskrobot.integration.tool.local

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.data.hotword.HotwordController
import com.example.mydeskrobot.domain.telephony.PhoneNumberNormalizer
import com.example.mydeskrobot.integration.telephony.CallSttPauseCoordinator
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Opens the system dialer with a pre-filled number ([Intent.ACTION_DIAL]).
 * Does not place a call directly — the user confirms on the phone app.
 */
class DialPhoneTool(
  private val context: Context,
  private val robotContextRepository: RobotContextRepository,
  private val callSttPauseCoordinator: CallSttPauseCoordinator,
) : Tool {

  constructor(context: Context) : this(
    context = context,
    robotContextRepository = RobotContextRepository(context),
    callSttPauseCoordinator = CallSttPauseCoordinator.get(context),
  )

  override val name: String = "dial_phone"
  override val locality: ToolLocality = ToolLocality.LOCAL

  override fun getDefinition(): ToolDefinition {
    return ToolDefinition(
      name = name,
      description = "Open the phone dialer with a number pre-filled. User taps Call to start. " +
        "Does not auto-dial. Use after resolve_phone_contact + user confirmation, or with explicit number.",
      parameters = listOf(
        ToolParameter(
          name = "number",
          type = "string",
          description = "Phone number (e.g. +39 333 1234567, 3331234567)",
          required = true,
        ),
        ToolParameter(
          name = "set_call_context",
          type = "boolean",
          description = "If true (default), set robot context to call until voice session ends",
          required = false,
        ),
      ),
      returns = "success, number, dialer_opened",
      example = """{"name": "dial_phone", "params": {"number": "+39 333 1234567"}, "await_result": false}""",
    )
  }

  override suspend fun execute(invocation: ToolInvocation): ToolResult {
    val rawNumber = invocation.params["number"]?.toString()?.trim().orEmpty()
    if (rawNumber.isBlank()) {
      return ToolResult.Error(
        message = "Parametro 'number' mancante",
        code = "MISSING_PARAM",
      )
    }

    val number = PhoneNumberNormalizer.normalize(rawNumber)
      ?: return ToolResult.Error(
        message = "Numero non valido: $rawNumber",
        code = "INVALID_NUMBER",
      )

    val setCallContext = parseBoolean(invocation.params["set_call_context"], default = true)
    if (setCallContext) {
      robotContextRepository.applyFromToolParams(
        mapOf(
          "profile" to "call",
          "notifications" to "silent",
        ),
      )
    }

    HotwordController.beginAssistantTurn()
    callSttPauseCoordinator.onDialLaunched()

    return try {
      val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      Log.i(TAG, "Dialer opened for $number (raw=$rawNumber)")

      ToolResult.Success(
        data = mapOf(
          "success" to true,
          "number" to number,
          "dialer_opened" to true,
          "user_action_required" to "Tap Call on the phone app to start the call",
          "stt_pause" to "Resumes automatically after call ends if phone permission granted",
        ),
      )
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, "No dialer for tel:$number", e)
      callSttPauseCoordinator.unregister()
      HotwordController.endPhoneCallHold()
      ToolResult.Error(
        message = "Nessuna app telefono disponibile",
        code = "NO_DIALER",
        recoverable = true,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Dial error: ${e.message}", e)
      callSttPauseCoordinator.unregister()
      HotwordController.endPhoneCallHold()
      ToolResult.Error(
        message = "Impossibile aprire il dialer: ${e.message}",
        code = "DIAL_ERROR",
        recoverable = true,
      )
    }
  }

  private fun parseBoolean(raw: Any?, default: Boolean): Boolean =
    when (raw) {
      null -> default
      is Boolean -> raw
      is String -> raw.equals("true", ignoreCase = true)
      else -> default
    }

  companion object {
    private const val TAG = "DialPhoneTool"
  }
}
