package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.ChainStatus
import com.example.mydeskrobot.reasoning.model.LlmAction
import org.junit.Assert.*
import org.junit.Test

class LlmResponseParserTest {
    
    private val parser = LlmResponseParser()
    
    @Test
    fun `parse simple reply with no action`() {
        val json = """
            {
                "reply": "Ciao, come posso aiutarti?",
                "emotion": "happy"
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertEquals("Ciao, come posso aiutarti?", result.text)
        assertEquals("happy", result.emotion)
        assertEquals(LlmAction.None, result.action)
    }
    
    @Test
    fun `parse reply with action none`() {
        val json = """
            {
                "reply": "Ecco la risposta",
                "emotion": "neutral",
                "action": {
                    "type": "none"
                }
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertEquals("Ecco la risposta", result.text)
        assertEquals(LlmAction.None, result.action)
    }
    
    @Test
    fun `parse reply with tool call action`() {
        val json = """
            {
                "reply": "Controllo il meteo",
                "emotion": "thinking",
                "action": {
                    "type": "tool_call",
                    "tools": [
                        {
                            "name": "get_weather",
                            "params": {"city": "Roma"},
                            "await_result": true
                        }
                    ],
                    "chain_status": "in_progress"
                }
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertEquals("Controllo il meteo", result.text)
        assertEquals("thinking", result.emotion)
        
        val action = result.action
        assertTrue(action is LlmAction.ToolCall)
        
        val toolCall = action as LlmAction.ToolCall
        assertEquals(1, toolCall.tools.size)
        assertEquals("get_weather", toolCall.tools[0].name)
        assertEquals("Roma", toolCall.tools[0].params["city"])
        assertEquals(true, toolCall.tools[0].awaitResult)
        assertEquals(ChainStatus.IN_PROGRESS, toolCall.chainStatus)
    }
    
    @Test
    fun `parse reply with multiple tools`() {
        val json = """
            {
                "reply": "Faccio due cose",
                "action": {
                    "type": "tool_call",
                    "tools": [
                        {"name": "tool1", "params": {}},
                        {"name": "tool2", "params": {"key": "value"}}
                    ],
                    "parallel": true
                }
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        val action = result.action as LlmAction.ToolCall
        assertEquals(2, action.tools.size)
        assertEquals("tool1", action.tools[0].name)
        assertEquals("tool2", action.tools[1].name)
        assertTrue(action.parallel)
    }
    
    @Test
    fun `parse reply with confirm required`() {
        val json = """
            {
                "reply": "Vuoi che apra il browser?",
                "emotion": "neutral",
                "action": {
                    "type": "confirm_required",
                    "tools": [
                        {"name": "open_browser", "params": {"url": "https://example.com"}}
                    ],
                    "confirmPrompt": "Devo aprire il sito example.com?"
                }
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        val action = result.action
        assertTrue(action is LlmAction.ConfirmRequired)
        
        val confirm = action as LlmAction.ConfirmRequired
        assertEquals("open_browser", confirm.tool.name)
        assertEquals("Devo aprire il sito example.com?", confirm.confirmPrompt)
    }
    
    @Test
    fun `parse legacy imageRequired as take_photo tool`() {
        val json = """
            {
                "reply": "Ok, do un'occhiata",
                "emotion": "surprised",
                "imageRequired": true
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        assertEquals("Ok, do un'occhiata", result.text)
        
        val action = result.action
        assertTrue(action is LlmAction.ToolCall)
        
        val toolCall = action as LlmAction.ToolCall
        assertEquals(1, toolCall.tools.size)
        assertEquals("take_photo", toolCall.tools[0].name)
    }
    
    @Test
    fun `parse plain text fallback`() {
        val plainText = "Questa è una risposta semplice senza JSON"
        
        val result = parser.parse(plainText)
        
        assertEquals(plainText, result.text)
        assertNull(result.emotion)
        assertEquals(LlmAction.None, result.action)
    }
    
    @Test
    fun `parse json in markdown fence`() {
        val fenced = """
            ```json
            {"reply": "Risposta nel fence", "emotion": "happy"}
            ```
        """.trimIndent()
        
        val result = parser.parse(fenced)
        
        assertEquals("Risposta nel fence", result.text)
        assertEquals("happy", result.emotion)
    }
    
    @Test
    fun `parse json embedded in text`() {
        val embedded = """
            Ecco la mia risposta: {"reply": "Ciao", "emotion": "neutral"} e altri dettagli.
        """.trimIndent()
        
        val result = parser.parse(embedded)
        
        assertEquals("Ciao", result.text)
        assertEquals("neutral", result.emotion)
    }
    
    @Test
    fun `parse chain_status complete`() {
        val json = """
            {
                "reply": "Fatto!",
                "action": {
                    "type": "tool_call",
                    "tools": [{"name": "final_tool", "params": {}}],
                    "chain_status": "complete"
                }
            }
        """.trimIndent()
        
        val result = parser.parse(json)
        
        val action = result.action as LlmAction.ToolCall
        assertEquals(ChainStatus.COMPLETE, action.chainStatus)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `parse empty string throws`() {
        parser.parse("")
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun `parse whitespace only throws`() {
        parser.parse("   \n\t  ")
    }
    
    @Test
    fun `emotion is normalized to lowercase`() {
        val json = """{"reply": "test", "emotion": "HAPPY"}"""
        
        val result = parser.parse(json)
        
        assertEquals("happy", result.emotion)
    }
    
    @Test
    fun `text field fallback to reply`() {
        val json = """{"text": "Usando text field"}"""
        
        val result = parser.parse(json)
        
        assertEquals("Usando text field", result.text)
    }

    @Test
    fun `parse speak_confidence present`() {
        val json = """
            {
                "reply": "Promemoria tra poco",
                "emotion": "happy",
                "speak_confidence": 0.85
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertEquals(0.85, result.speakConfidence!!, 0.001)
    }

    @Test
    fun `parse speak_confidence missing returns null`() {
        val json = """
            {
                "reply": "Ciao",
                "emotion": "neutral"
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertNull(result.speakConfidence)
    }

    @Test
    fun `parse speak_confidence clamped to 0-1 range`() {
        val jsonHigh = """{"reply": "test", "speak_confidence": 1.5}"""
        val jsonLow = """{"reply": "test", "speak_confidence": -0.2}"""

        val resultHigh = parser.parse(jsonHigh)
        val resultLow = parser.parse(jsonLow)

        assertEquals(1.0, resultHigh.speakConfidence!!, 0.001)
        assertEquals(0.0, resultLow.speakConfidence!!, 0.001)
    }

    @Test
    fun `parse think field on in_progress chain without affecting spoken text`() {
        val json = """
            {
                "reply": "",
                "think": "Last photo empty — rotating base_pan +20 and retaking.",
                "emotion": "thinking",
                "speak_confidence": 0.0,
                "action": {
                    "type": "tool_call",
                    "tools": [{"name": "take_photo", "params": {}}],
                    "chain_status": "in_progress"
                }
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertEquals("", result.text)
        assertEquals("Last photo empty — rotating base_pan +20 and retaking.", result.think)
        val action = result.action as LlmAction.ToolCall
        assertEquals(ChainStatus.IN_PROGRESS, action.chainStatus)
    }

    @Test
    fun `parse blank think as null`() {
        val json = """{"reply": "Ciao", "think": "", "emotion": "happy"}"""

        val result = parser.parse(json)

        assertNull(result.think)
        assertEquals("Ciao", result.text)
    }

    @Test
    fun `parse heartbeat response with empty reply and zero confidence`() {
        val json = """
            {
                "reply": "",
                "emotion": "neutral",
                "speak_confidence": 0.0,
                "action": {"type": "none"}
            }
        """.trimIndent()

        val result = parser.parse(json)

        assertEquals("", result.text)
        assertEquals(0.0, result.speakConfidence!!, 0.001)
        assertEquals(LlmAction.None, result.action)
    }
}
