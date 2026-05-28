package com.example.mydeskrobot.reasoning.tool

import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult

/**
 * Interface for tool execution.
 * The Reasoning Module uses this to execute tools without knowing
 * where they run (local, remote, hardware).
 * 
 * Platform-agnostic: no Android dependencies.
 */
interface ToolExecutor {
    /**
     * Execute a tool invocation.
     * 
     * @param invocation Tool to execute with parameters
     * @return Result of the execution
     */
    suspend fun execute(invocation: ToolInvocation): ToolResult
    
    /**
     * Get definitions of all available tools.
     * Used to build the system prompt.
     */
    fun getAvailableTools(): List<ToolDefinition>
    
    /**
     * Check if a tool is available.
     */
    fun isToolAvailable(name: String): Boolean =
        getAvailableTools().any { it.name == name }
}
