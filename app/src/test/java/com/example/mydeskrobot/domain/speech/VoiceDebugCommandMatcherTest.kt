package com.example.mydeskrobot.domain.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceDebugCommandMatcherTest {

    @Test
    fun matchesForceHeartbeat_recognizesItalianPhrases() {
        assertTrue(VoiceDebugCommandMatcher.matchesForceHeartbeat("forza heartbeat"))
        assertTrue(VoiceDebugCommandMatcher.matchesForceHeartbeat("robot forza proattività"))
        assertTrue(VoiceDebugCommandMatcher.matchesForceHeartbeat("debug heartbeat per favore"))
    }

    @Test
    fun matchesForceHeartbeat_rejectsUnrelated() {
        assertFalse(VoiceDebugCommandMatcher.matchesForceHeartbeat("che tempo fa"))
        assertFalse(VoiceDebugCommandMatcher.matchesForceHeartbeat("heartbeat"))
    }
}
