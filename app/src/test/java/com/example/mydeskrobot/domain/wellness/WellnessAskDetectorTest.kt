package com.example.mydeskrobot.domain.wellness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessAskDetectorTest {

    @Test
    fun `detects common italian wellness asks`() {
        assertTrue(WellnessAskDetector.isWellnessAsk("come sto?"))
        assertTrue(WellnessAskDetector.isWellnessAsk("Come vanno i pasti oggi"))
        assertTrue(WellnessAskDetector.isWellnessAsk("dimmi il wellness"))
        assertTrue(WellnessAskDetector.isWellnessAsk("stato wellness per favore"))
    }

    @Test
    fun `rejects unrelated phrases`() {
        assertFalse(WellnessAskDetector.isWellnessAsk("che tempo fa"))
        assertFalse(WellnessAskDetector.isWellnessAsk("ricordami di chiamare Marco"))
        assertFalse(WellnessAskDetector.isWellnessAsk(""))
    }
}
