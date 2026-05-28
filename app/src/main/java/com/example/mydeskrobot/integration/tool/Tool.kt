package com.example.mydeskrobot.integration.tool

import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition

/**
 * Base interface for tool implementations.
 * Each tool knows how to execute itself and provide its definition.
 */
interface Tool {
    /** Unique tool name */
    val name: String
    
    /** Where this tool executes */
    val locality: ToolLocality
    
    /** Tool definition for system prompt */
    fun getDefinition(): ToolDefinition
    
    /** Execute the tool with given invocation */
    suspend fun execute(invocation: ToolInvocation): ToolResult
}

/**
 * Where a tool executes.
 */
enum class ToolLocality {
    /** Executed locally on the Android device */
    LOCAL,
    
    /** Executed via HTTP to external service */
    REMOTE,
    
    /** Executed on ESP32 hardware via BLE/WiFi */
    HARDWARE,
}
