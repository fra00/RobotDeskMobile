package com.example.mydeskrobot.domain.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionTriggerMatcherTest {

    @Test
    fun anyUserUtterance_triggersCentering() {
        assertTrue(AttentionTriggerMatcher.shouldCenterOnUser("ok"))
        assertTrue(AttentionTriggerMatcher.shouldCenterOnUser("grazie mille"))
        assertTrue(AttentionTriggerMatcher.shouldCenterOnUser("ciao"))
        assertTrue(AttentionTriggerMatcher.shouldCenterOnUser("come stai?"))
        assertTrue(AttentionTriggerMatcher.shouldCenterOnUser("imposta volume al massimo"))
    }

    @Test
    fun blank_doesNotTrigger() {
        assertFalse(AttentionTriggerMatcher.shouldCenterOnUser(""))
        assertFalse(AttentionTriggerMatcher.shouldCenterOnUser("   "))
    }
}
