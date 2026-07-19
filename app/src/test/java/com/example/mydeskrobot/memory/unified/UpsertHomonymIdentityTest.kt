package com.example.mydeskrobot.memory.unified

import com.example.mydeskrobot.memory.db.MemoryCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UpsertHomonymIdentityTest {

    @Test
    fun homonym_contact_and_user_name_remain_separate_rows() = runTest {
        val repository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao())
        repository.upsertUserFacingFact(
            category = MemoryCategory.IDENTITY,
            value = "L'utente si chiama Francesco",
            confidence = 0.9f,
            source = MemoryDocumentSource.TOOL,
            isPinned = true,
        )
        repository.upsertUserFacingFact(
            category = MemoryCategory.FACT,
            value = "Francesco Rossi è un contatto WhatsApp",
            confidence = 0.85f,
            source = MemoryDocumentSource.TOOL,
        )

        val active = repository.getUserFacingActiveDocuments()
        assertEquals(2, active.size)
    }
}
