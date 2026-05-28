package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.example.mydeskrobot.reasoning.model.LlmAction
import com.example.mydeskrobot.reasoning.model.ToolResult
import org.junit.Assert.*
import org.junit.Test

class ConversationHistoryTest {
    
    @Test
    fun `addUserMessage adds user message`() {
        val history = ConversationHistory()
        
        history.addUserMessage("Ciao")
        
        val messages = history.toMessages()
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].role)
        assertEquals("Ciao", messages[0].content)
    }
    
    @Test
    fun `addUserMessage ignores blank content`() {
        val history = ConversationHistory()
        
        history.addUserMessage("")
        history.addUserMessage("   ")
        
        assertTrue(history.isEmpty())
    }
    
    @Test
    fun `addAssistantMessage adds assistant message`() {
        val history = ConversationHistory()
        
        val response = ParsedLlmResponse(
            text = "Ecco la risposta",
            emotion = "happy",
            action = LlmAction.None,
        )
        history.addAssistantMessage(response)
        
        val messages = history.toMessages()
        assertEquals(1, messages.size)
        assertEquals("assistant", messages[0].role)
        assertEquals("Ecco la risposta", messages[0].content)
    }
    
    @Test
    fun `addAssistantMessage with action appends marker`() {
        val history = ConversationHistory()
        
        val response = ParsedLlmResponse(
            text = "Controllo il meteo",
            action = LlmAction.ToolCall(
                tools = listOf(),
            ),
        )
        history.addAssistantMessage(response)
        
        val messages = history.toMessages()
        assertTrue(messages[0].content.contains("[Action requested]"))
    }
    
    @Test
    fun `addToolResult adds formatted tool result`() {
        val history = ConversationHistory()
        
        history.addToolResult(
            toolName = "get_weather",
            result = ToolResult.Success(mapOf("temp" to 25)),
        )
        
        val messages = history.toMessages()
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].role)
        assertTrue(messages[0].content.contains("[TOOL_RESULT: get_weather]"))
    }
    
    @Test
    fun `addToolResult formats error correctly`() {
        val history = ConversationHistory()
        
        history.addToolResult(
            toolName = "test_tool",
            result = ToolResult.Error("Something went wrong"),
        )
        
        val messages = history.toMessages()
        assertTrue(messages[0].content.contains("ERROR: Something went wrong"))
    }
    
    @Test
    fun `history is trimmed when exceeding max`() {
        val history = ConversationHistory(maxMessages = 3)
        
        history.addUserMessage("Msg 1")
        history.addUserMessage("Msg 2")
        history.addUserMessage("Msg 3")
        history.addUserMessage("Msg 4")
        
        val messages = history.toMessages()
        assertEquals(3, messages.size)
        assertEquals("Msg 2", messages[0].content)
        assertEquals("Msg 4", messages[2].content)
    }
    
    @Test
    fun `clear removes all messages`() {
        val history = ConversationHistory()
        
        history.addUserMessage("Test 1")
        history.addUserMessage("Test 2")
        
        history.clear()
        
        assertTrue(history.isEmpty())
        assertEquals(0, history.size())
    }
    
    @Test
    fun `toMessages returns copy`() {
        val history = ConversationHistory()
        history.addUserMessage("Test")
        
        val messages = history.toMessages()
        val messagesAgain = history.toMessages()
        
        assertNotSame(messages, messagesAgain)
    }
}
