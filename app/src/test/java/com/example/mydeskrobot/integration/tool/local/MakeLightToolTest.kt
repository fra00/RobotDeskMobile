package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.data.light.DeskLightController
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MakeLightToolTest {

    @After
    fun tearDown() {
        DeskLightController.setBrightMode(false)
    }

    @Test
    fun execute_turnsBrightModeOn() = runBlocking {
        val tool = MakeLightTool()
        val result = tool.execute(
            ToolInvocation(name = "make_light", params = mapOf("on" to true)),
        )
        assertTrue(result is ToolResult.Success)
        assertTrue(DeskLightController.isBrightMode.value)
    }

    @Test
    fun execute_turnsBrightModeOff() = runBlocking {
        DeskLightController.setBrightMode(true)
        val tool = MakeLightTool()
        val result = tool.execute(
            ToolInvocation(name = "make_light", params = mapOf("on" to false)),
        )
        assertTrue(result is ToolResult.Success)
        assertEquals(false, DeskLightController.isBrightMode.value)
    }
}
