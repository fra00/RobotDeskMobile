package com.example.mydeskrobot.integration.tool.local

import com.example.mydeskrobot.domain.memory.MemoryWriteTurnContext
import com.example.mydeskrobot.integration.memory.FakeMemoryDao
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.reasoning.model.ToolInvocation
import com.example.mydeskrobot.reasoning.model.ToolResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveMemoryToolTest {

    @After
    fun tearDown() {
        MemoryWriteTurnContext.blockUserSelfNameIdentity = false
    }

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

    @Test
    fun execute_rejectsConflictingUserSelfNameWithoutReplaceFlag() = runBlocking {
        val repository = UserMemoryRepository.createForTest(FakeMemoryDao())
        val tool = SaveMemoryTool(repository)
        val first = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "L'utente si chiama Francesco",
                    "category" to "IDENTITY",
                    "pinned" to true,
                ),
            ),
        )
        assertTrue(first is ToolResult.Success)

        val second = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "L'utente si chiama Roberta",
                    "category" to "IDENTITY",
                    "pinned" to true,
                ),
            ),
        )
        assertTrue(second is ToolResult.Error)
        assertEquals("USER_NAME_CONFLICT", (second as ToolResult.Error).code)
    }

    @Test
    fun execute_replaceUserNameOverwritesConflictingSelfName() = runBlocking {
        val repository = UserMemoryRepository.createForTest(FakeMemoryDao())
        val tool = SaveMemoryTool(repository)
        tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "L'utente si chiama Francesco",
                    "category" to "IDENTITY",
                ),
            ),
        )
        val replaced = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "L'utente si chiama Roberta",
                    "category" to "IDENTITY",
                    "replace_user_name" to true,
                ),
            ),
        )
        assertTrue(replaced is ToolResult.Success)
        val names = repository.getByCategory(MemoryCategory.IDENTITY, limit = 10)
            .map { it.value }
        assertEquals(listOf("L'utente si chiama Roberta"), names)
    }

    @Test
    fun execute_blocksUserSelfNameOnSystemTurn() = runBlocking {
        MemoryWriteTurnContext.blockUserSelfNameIdentity = true
        val tool = SaveMemoryTool(UserMemoryRepository.createForTest(FakeMemoryDao()))
        val result = tool.execute(
            ToolInvocation(
                name = "save_memory",
                params = mapOf(
                    "value" to "L'utente si chiama Simone",
                    "category" to "IDENTITY",
                ),
            ),
        )
        assertTrue(result is ToolResult.Error)
        assertEquals("USER_NAME_BLOCKED_SYSTEM_TURN", (result as ToolResult.Error).code)
    }
}
