package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.reasoning.model.ToolInvocation
import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningLogBufferTest {

    @Test
    fun describeToolMode_fireAndCheck_whenParamTrue() {
        val tool = ToolInvocation(
            name = "open_browser",
            params = mapOf("url" to "https://example.com", "fire_and_check" to true),
            awaitResult = false,
        )
        assertEquals("fire-and-check", ReasoningLogBuffer.describeToolMode(tool))
    }

    @Test
    fun describeToolMode_fireAndForget_whenAwaitResultFalse() {
        val tool = ToolInvocation(
            name = "open_browser",
            params = mapOf("url" to "https://example.com"),
            awaitResult = false,
        )
        assertEquals("fire-and-forget", ReasoningLogBuffer.describeToolMode(tool))
    }

    @Test
    fun describeToolMode_await_whenAwaitResultTrue() {
        val tool = ToolInvocation(
            name = "web_search",
            params = mapOf("query" to "meteo"),
            awaitResult = true,
        )
        assertEquals("await", ReasoningLogBuffer.describeToolMode(tool))
    }
}
