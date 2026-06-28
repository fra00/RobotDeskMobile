package com.example.mydeskrobot.integration.tool

import android.util.Log
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import com.example.mydeskrobot.reasoning.tool.ToolDefinition
import com.example.mydeskrobot.reasoning.tool.ToolExecutor

/**
 * Routes tool invocations to the appropriate executor based on tool locality.
 * Implements [ToolExecutor] interface from the Reasoning Module.
 */
class ToolRouter(
    private val tools: List<Tool> = emptyList(),
) : ToolExecutor {
    
    private val toolMap: Map<String, Tool> = tools.associateBy { it.name }
    
    override suspend fun execute(invocation: ToolInvocation): ToolResult {
        Log.i(TAG, "Tool call: ${invocation.name} params=${invocation.params}")
        
        val tool = toolMap[invocation.name]
        if (tool == null) {
            Log.w(TAG, "Tool sconosciuto: ${invocation.name}. Disponibili: ${toolMap.keys}")
            val message = when (invocation.name) {
                "move_body_joint", "move_body_joints", "body_home", "body_status" ->
                    "Corpo fisico non attivo. Apri Impostazioni → Corpo robot, abilita il corpo, " +
                        "inserisci l'URL (es. http://192.168.x.x) e tocca Salva."
                else -> "Tool sconosciuto: ${invocation.name}"
            }
            return ToolResult.Error(
                message = message,
                code = "UNKNOWN_TOOL",
                recoverable = false,
            )
        }
        
        return try {
            val result = tool.execute(invocation)
            Log.i(TAG, "Tool ${invocation.name} result: ${result::class.simpleName}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Errore esecuzione ${invocation.name}: ${e.message}", e)
            ToolResult.Error(
                message = "Errore durante l'esecuzione di ${invocation.name}: ${e.message}",
                code = "EXECUTION_ERROR",
                recoverable = true,
            )
        }
    }
    
    override fun getAvailableTools(): List<ToolDefinition> {
        return tools.map { it.getDefinition() }
    }
    
    override fun isToolAvailable(name: String): Boolean {
        return toolMap.containsKey(name)
    }
    
    /**
     * Create a new router with additional tools.
     */
    fun withTools(additionalTools: List<Tool>): ToolRouter {
        return ToolRouter(tools + additionalTools)
    }
    
    /**
     * Create a new router with a single additional tool.
     */
    fun withTool(tool: Tool): ToolRouter {
        return ToolRouter(tools + tool)
    }
    
    companion object {
        private const val TAG = "ToolRouter"
        
        /**
         * Create an empty router.
         */
        fun empty(): ToolRouter = ToolRouter(emptyList())
    }
}
