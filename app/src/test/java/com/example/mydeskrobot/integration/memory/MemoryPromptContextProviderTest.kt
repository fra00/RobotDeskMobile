package com.example.mydeskrobot.integration.memory

import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptContextProviderTest {

    private fun entity(
        id: Long,
        category: MemoryCategory,
        value: String,
    ) = MemoryItemEntity(
        id = id,
        category = category,
        value = value,
        confidence = 0.9f,
        createdAt = 1L,
        updatedAt = 1L,
        sourceMessageId = 0L,
    )

    @Test
    fun queryProfile_includesDogMemory() = runTest {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.FACT, "L'utente ha un cane di nome Brina"),
                entity(2L, MemoryCategory.PREFERENCE, "L'utente segue la MotoGP"),
            ),
        )
        val provider = MemoryPromptContextProviderImpl(UserMemoryRepository.createForTest(dao))

        val context = provider.buildContextFor("Come si chiama il mio cane?")

        assertTrue(context.contains("KNOWN USER MEMORY"))
        assertTrue(context.contains("Brina"))
    }

    @Test
    fun visionProfile_includesFactCatalog() = runTest {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.FACT, "L'utente ha un cane di nome Brina"),
                entity(2L, MemoryCategory.ROUTINE, "Il laboratorio è in fondo al corridoio"),
                entity(3L, MemoryCategory.PREFERENCE, "L'utente segue la MotoGP"),
            ),
        )
        val provider = MemoryPromptContextProviderImpl(UserMemoryRepository.createForTest(dao))

        val context = provider.buildContextFor(
            userText = "Fai una foto",
            profileOverride = MemoryRetrievalProfile.VISION,
        )

        assertTrue(context.contains("KNOWN ENTITIES FOR VISION"))
        assertTrue(context.contains("Brina") || context.contains("cane"))
        assertTrue(context.contains("laboratorio") || context.contains("corridoio"))
    }

    @Test
    fun leisureProfile_includesPreferences() = runTest {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.PREFERENCE, "L'utente segue la MotoGP"),
            ),
        )
        val provider = MemoryPromptContextProviderImpl(UserMemoryRepository.createForTest(dao))

        val context = provider.buildContextFor("Cosa posso guardare oggi?")

        assertTrue(context.contains("USER PREFERENCES"))
        assertTrue(context.contains("MotoGP"))
    }

    @Test
    fun defaultProfile_excludesRobotInternalMemories() = runTest {
        val dao = FakeMemoryDao(
            listOf(
                entity(1L, MemoryCategory.FACT, "L'utente ha un cane di nome Brina"),
                entity(2L, MemoryCategory.INTENT, "INTENT: monitorare pranzo"),
                entity(3L, MemoryCategory.OBSERVATION, "12 giugno 2026: ancora al desk"),
            ),
        )
        val provider = MemoryPromptContextProviderImpl(UserMemoryRepository.createForTest(dao))

        val context = provider.buildContextFor("Parlami del mio cane")

        assertTrue(context.contains("Brina"))
        assertTrue(!context.contains("monitorare pranzo"))
        assertTrue(!context.contains("ancora al desk"))
    }
}
