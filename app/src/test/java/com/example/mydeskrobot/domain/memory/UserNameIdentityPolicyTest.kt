package com.example.mydeskrobot.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserNameIdentityPolicyTest {

    @Test
    fun `detects stored user-self name claims`() {
        assertEquals(
            "Francesco",
            UserNameIdentityPolicy.extractDisplayedName("L'utente si chiama Francesco"),
        )
        assertEquals(
            "Roberta",
            UserNameIdentityPolicy.extractDisplayedName("Utente si chiama Roberta"),
        )
        assertTrue(UserNameIdentityPolicy.isUserSelfNameClaim("mi chiamo Simone"))
    }

    @Test
    fun `ignores contact and pet names`() {
        assertNull(UserNameIdentityPolicy.extractDisplayedName("Il cane si chiama Brina"))
        assertNull(UserNameIdentityPolicy.extractDisplayedName("La mamma si chiama Anna"))
        assertNull(UserNameIdentityPolicy.extractDisplayedName("Francesco Rossi contatto WhatsApp"))
        assertFalse(UserNameIdentityPolicy.isUserSelfNameClaim("L'utente è sviluppatore web"))
    }

    @Test
    fun `sameUserName is case insensitive`() {
        assertTrue(UserNameIdentityPolicy.sameUserName("Francesco", "francesco"))
        assertFalse(UserNameIdentityPolicy.sameUserName("Francesco", "Simone"))
    }

    @Test
    fun `extractor skips self-name without user role`() {
        assertTrue(
            UserNameIdentityPolicy.shouldSkipExtractorUserSelfName(
                categoryName = "IDENTITY",
                value = "L'utente si chiama Simone",
                entryRoles = listOf("system", "assistant"),
            ),
        )
        assertFalse(
            UserNameIdentityPolicy.shouldSkipExtractorUserSelfName(
                categoryName = "IDENTITY",
                value = "L'utente si chiama Simone",
                entryRoles = listOf("user", "assistant"),
            ),
        )
        assertFalse(
            UserNameIdentityPolicy.shouldSkipExtractorUserSelfName(
                categoryName = "FACT",
                value = "L'utente si chiama Simone",
                entryRoles = listOf("system"),
            ),
        )
    }
}
