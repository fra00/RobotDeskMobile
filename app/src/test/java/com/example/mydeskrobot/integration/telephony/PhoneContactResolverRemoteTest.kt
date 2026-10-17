package com.example.mydeskrobot.integration.telephony

import com.example.mydeskrobot.memory.unified.FakeMemoryDocumentDao
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneContactResolverRemoteTest {

    private class LocalLookup(
        private val matches: List<ContactPhoneMatch> = emptyList(),
        private val permission: Boolean = true,
    ) : ContactPhoneLookup {
        override fun hasPermission(): Boolean = permission
        override fun search(query: String): List<ContactPhoneMatch> = matches
    }

    @Test
    fun `remote contact used when local empty`() = runBlocking {
        val remote = listOf(
            ContactPhoneMatch(
                displayName = "Anna",
                number = "+391234",
                source = ContactPhoneSource.CONTACTS,
                score = 0.95f,
            ),
        )
        val resolver = PhoneContactResolver(
            contactsResolver = LocalLookup(permission = false),
            unifiedMemoryRepository = UnifiedMemoryRepository.createForTest(FakeMemoryDocumentDao()),
            remoteSearch = { remote },
        )
        val result = resolver.resolve("Anna")
        assertTrue(result is PhoneContactResolveResult.Single)
        assertEquals("Anna", (result as PhoneContactResolveResult.Single).match.displayName)
        assertEquals("+391234", result.match.number)
    }
}
