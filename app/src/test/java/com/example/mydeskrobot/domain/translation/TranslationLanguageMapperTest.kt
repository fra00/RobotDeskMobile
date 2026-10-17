package com.example.mydeskrobot.domain.translation

import com.example.mydeskrobot.reasoning.model.TranslationModeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguageMapperTest {

    @Test
    fun `resolve inglese to en-US`() {
        val resolved = TranslationLanguageMapper.resolve("inglese")!!
        assertEquals("en-US", resolved.languageTag)
        assertEquals("English", resolved.label)
    }

    @Test
    fun `resolve french variants`() {
        assertEquals("fr-FR", TranslationLanguageMapper.resolve("francese")!!.languageTag)
        assertEquals("fr-FR", TranslationLanguageMapper.resolve("fr")!!.languageTag)
    }

    @Test
    fun `resolve blank returns null`() {
        assertNull(TranslationLanguageMapper.resolve(null))
        assertNull(TranslationLanguageMapper.resolve("  "))
    }

    @Test
    fun `unknown label defaults to en-US tag with original label`() {
        val resolved = TranslationLanguageMapper.resolve("swahili")!!
        assertEquals("en-US", resolved.languageTag)
        assertEquals("swahili", resolved.label)
    }
}

class TranslationModePolicyTest {

    @Test
    fun `prompt empty when disabled`() {
        assertEquals("", TranslationModePolicy.buildPromptSection(TranslationModeState.OFF))
    }

    @Test
    fun `prompt contains active translation mode when enabled`() {
        val section = TranslationModePolicy.buildPromptSection(
            TranslationModeState(
                enabled = true,
                targetLanguageTag = "en-US",
                targetLanguageLabel = "English",
            ),
        )
        assertTrue(section.contains("ACTIVE TRANSLATION MODE"))
        assertTrue(section.contains("English"))
        assertTrue(section.contains("set_translation_mode"))
    }

    @Test
    fun `tts tag only when mode stayed on across turn`() {
        val on = TranslationModeState(enabled = true, targetLanguageTag = "en-US", targetLanguageLabel = "English")
        val off = TranslationModeState.OFF

        assertEquals("en-US", TranslationModePolicy.ttsLanguageTagForTurn(on, on))
        assertNull(TranslationModePolicy.ttsLanguageTagForTurn(off, on)) // enter ack
        assertNull(TranslationModePolicy.ttsLanguageTagForTurn(on, off)) // exit confirm
        assertNull(TranslationModePolicy.ttsLanguageTagForTurn(off, off))
    }
}
