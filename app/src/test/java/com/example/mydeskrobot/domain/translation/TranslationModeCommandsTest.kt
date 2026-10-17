package com.example.mydeskrobot.domain.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationModeCommandsTest {

    @Test
    fun `enable without target_language fails`() {
        val error = runCatching {
            TranslationModeCommands.parse(mapOf("enabled" to true))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("target_language"))
    }

    @Test
    fun `enable with inglese succeeds`() {
        val state = TranslationModeCommands.parse(
            mapOf("enabled" to true, "target_language" to "inglese"),
        )
        assertTrue(state.enabled)
        assertEquals("en-US", state.targetLanguageTag)
        assertEquals("English", state.targetLanguageLabel)
    }

    @Test
    fun `disable returns off`() {
        val state = TranslationModeCommands.parse(mapOf("enabled" to false))
        assertFalse(state.enabled)
    }

    @Test
    fun `missing enabled fails`() {
        val error = runCatching {
            TranslationModeCommands.parse(emptyMap())
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
