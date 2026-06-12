package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.model.ChainStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainSpeechPolicyTest {

    @Test
    fun inProgress_suppressesIntermediateSpeech() {
        assertTrue(ChainSpeechPolicy.suppressIntermediateSpeech(ChainStatus.IN_PROGRESS))
    }

    @Test
    fun complete_doesNotSuppressIntermediateSpeech() {
        assertFalse(ChainSpeechPolicy.suppressIntermediateSpeech(ChainStatus.COMPLETE))
    }

    @Test
    fun failed_doesNotSuppressIntermediateSpeech() {
        assertFalse(ChainSpeechPolicy.suppressIntermediateSpeech(ChainStatus.FAILED))
    }
}
