package com.example.mydeskrobot.integration.tool.local

import android.content.Context
import android.media.AudioManager
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter

/**
 * Volume control tool.
 * Uses AudioManager to control media volume.
 */
class VolumeTool(
    private val context: Context,
) : Tool {
    
    override val name: String = "set_volume"
    override val locality: ToolLocality = ToolLocality.LOCAL
    
    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Set or adjust media volume",
            parameters = listOf(
                ToolParameter(
                    name = "action",
                    type = "string",
                    description = "Action: 'set' (absolute), 'up', 'down', 'mute', 'unmute'",
                    required = true,
                ),
                ToolParameter(
                    name = "level",
                    type = "integer",
                    description = "Volume level 0-100 (only for action 'set')",
                    required = false,
                ),
            ),
            returns = "current_volume (int 0-100)",
            example = """{"name": "set_volume", "params": {"action": "set", "level": 50}, "await_result": true}""",
        )
    }
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val action = invocation.params["action"]?.toString()?.lowercase()
            ?: return ToolResult.Error(
                message = "Parametro 'action' mancante",
                code = "MISSING_PARAM",
            )
        
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolResult.Error(
                message = "AudioManager non disponibile",
                code = "SYSTEM_ERROR",
                recoverable = false,
            )
        
        return try {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            when (action) {
                "set" -> {
                    val level = (invocation.params["level"] as? Number)?.toInt()
                        ?: return ToolResult.Error(
                            message = "Parametro 'level' mancante per action 'set'",
                            code = "MISSING_PARAM",
                        )
                    
                    val clampedLevel = level.coerceIn(0, 100)
                    val volumeIndex = (clampedLevel * maxVolume / 100.0).toInt()
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        volumeIndex,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                
                "up" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                
                "down" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                
                "mute" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                
                "unmute" -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_UNMUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                }
                
                else -> {
                    return ToolResult.Error(
                        message = "Azione non riconosciuta: $action",
                        code = "INVALID_ACTION",
                    )
                }
            }
            
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val currentPercent = (currentVolume * 100.0 / maxVolume).toInt()
            
            ToolResult.Success(
                data = mapOf(
                    "current_volume" to currentPercent,
                    "action" to action,
                )
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Errore durante la modifica del volume: ${e.message}",
                code = "VOLUME_ERROR",
                recoverable = true,
            )
        }
    }
}
