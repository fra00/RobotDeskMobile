package com.example.mydeskrobot.integration.tool.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.R
import com.example.mydeskrobot.integration.tool.Tool
import com.example.mydeskrobot.integration.tool.ToolLocality
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolParameter
import java.util.concurrent.atomic.AtomicInteger

/**
 * Notification tool for showing system notifications.
 * Uses NotificationManager to create notifications.
 */
class NotificationTool(
    private val context: Context,
) : Tool {
    
    override val name: String = "show_notification"
    override val locality: ToolLocality = ToolLocality.LOCAL
    
    private val notificationId = AtomicInteger(1000)
    
    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = "Show a system notification",
            parameters = listOf(
                ToolParameter(
                    name = "title",
                    type = "string",
                    description = "Notification title",
                    required = true,
                ),
                ToolParameter(
                    name = "message",
                    type = "string",
                    description = "Notification body text",
                    required = true,
                ),
            ),
            returns = "notification_id (int)",
            example = """{"name": "show_notification", "params": {"title": "Promemoria", "message": "Riunione tra 10 minuti"}, "await_result": false}""",
        )
    }
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        val title = invocation.params["title"]?.toString()
            ?: return ToolResult.Error(
                message = "Parametro 'title' mancante",
                code = "MISSING_PARAM",
            )
        
        val message = invocation.params["message"]?.toString()
            ?: return ToolResult.Error(
                message = "Parametro 'message' mancante",
                code = "MISSING_PARAM",
            )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return ToolResult.Error(
                    message = "Permesso notifiche non concesso",
                    code = "PERMISSION_DENIED",
                    recoverable = false,
                )
            }
        }
        
        return try {
            ensureNotificationChannel()
            
            val id = notificationId.getAndIncrement()
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            
            NotificationManagerCompat.from(context).notify(id, notification)
            
            ToolResult.Success(
                data = mapOf(
                    "notification_id" to id,
                    "title" to title,
                )
            )
        } catch (e: SecurityException) {
            ToolResult.Error(
                message = "Permesso notifiche non concesso",
                code = "PERMISSION_DENIED",
                recoverable = false,
            )
        } catch (e: Exception) {
            ToolResult.Error(
                message = "Errore durante la creazione della notifica: ${e.message}",
                code = "NOTIFICATION_ERROR",
                recoverable = true,
            )
        }
    }
    
    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Robot Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche dal robot da scrivania"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        private const val CHANNEL_ID = "mydeskrobot_notifications"
    }
}
