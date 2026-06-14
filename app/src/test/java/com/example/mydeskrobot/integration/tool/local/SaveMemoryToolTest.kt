package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.integration.memory.FakeMemoryDao
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {

    @Test
    fun execute_savesObservationWithTtl() = runBlocking {
        val tool = SaveMemoryTool(UserMemoryRepository.createForTest(FakeMemoryDao()))

        val result = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "12 giugno 2026: utente al desk",
                    "category" to "OBSERVATION",
                    "ttl_days" to 7,
                ),
            ),
        )

        assertTrue(result is ToolResult.Success)
    }

    @Test
    fun execute_rejectsIntentCap() = runBlocking {
        val dao = FakeMemoryDao()
        val repository = UserMemoryRepository.createForTest(dao)
        val tool = SaveMemoryTool(repository)

        repeat(3) {
            tool.execute(
                ToolInvocation(
                    name = "save_memory",
                    params = mapOf(
                        "value" to "INTENT $it",
                        "category" to "INTENT",
                    ),
                ),
            )
        }

        val fourth = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "INTENT fourth",
                    "category" to "INTENT",
                ),
            ),
        )

        assertTrue(fourth is ToolResult.Error)
        assertEquals("INTENT_CAP_REACHED", (fourth as ToolResult.Error).code)
    }

    @Test
    fun execute_rejectsTtlOnUserFacingCategory() = runBlocking {
        val tool = SaveMemoryTool(UserMemoryRepository.createForTest(FakeMemoryDao()))

        val result = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "L'utente si chiama Marco",
                    "category" to "IDENTITY",
                    "ttl_days" to 7,
                ),
            ),
        )

        assertTrue(result is ToolResult.Error)
        assertEquals("INVALID_PARAM", (result as ToolResult.Error).code)
    }
}
