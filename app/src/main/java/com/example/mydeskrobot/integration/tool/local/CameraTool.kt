package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.domain.vision.VisionImageCapture
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

/**
 * Camera tool for capturing images.
 * Wraps [VisionImageCapture] to provide camera functionality as a tool.
 */
class CameraTool(
    private val visionCapture: VisionImageCapture,
) : Tool {
    
    override val name: String = "take_photo"
    override val locality: ToolLocality = ToolLocality.LOCAL
    
    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Capture a JPEG photo for visual analysis. Combinable with body tools in vision chains " +
                "(reorient, explore angles, persistent search). One image per LLM turn — if chaining multiple photos, leave brief text notes in history. " +
                "For find/cerca/verify goals or when the user says something exists (e.g. computer behind them) but it is not in frame: one photo does not end the task — reorient and search before denying absence.",
            parameters = emptyList(),
            returns = "JPEG image bytes for analysis",
            example = """{"name": "take_photo", "params": {}, "await_result": true}""",
        )
    }
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        return try {
            val result = visionCapture.captureJpeg()
            
            result.fold(
                onSuccess = { capturedImage ->
                    ToolResult.BinaryData(
                        data = capturedImage.jpegBytes,
                        mimeType = capturedImage.mimeType,
                        metadata = mapOf(
                            "size_bytes" to capturedImage.jpegBytes.size,
                        )
                    )
                },
                onFailure = { error ->
                    ToolResult.Error(
                        message = "Impossibile scattare la foto: ${error.message}",
                        code = "CAPTURE_ERROR",
                        recoverable = true,
                    )
                }
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Errore durante lo scatto: ${e.message}",
                code = "CAMERA_ERROR",
                recoverable = true,
            )
        }
    }
}
