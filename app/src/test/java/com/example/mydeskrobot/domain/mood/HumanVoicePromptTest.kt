package com.example.mydeskrobot.domain.mood

import org.junit.Assert.assertTrue
import org.junit.Test

class HumanVoicePromptTest {

    @Test
    fun `section bans assistant register and includes spoken examples`() {
        val text = HumanVoicePrompt.section()

        assertTrue(text.contains("VOCE UMANA"))
        assertTrue(text.contains("Certamente"))
        assertTrue(text.contains("Piove, porta l'ombrello"))
    }
}
