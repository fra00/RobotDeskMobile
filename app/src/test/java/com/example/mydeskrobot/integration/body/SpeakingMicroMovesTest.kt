package com.example.mydeskrobot.integration.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakingMicroMovesTest {

    @Test
    fun headTiltCycle_staysWithinSubtleRange() {
        repeat(20) { step ->
            val tilt = SpeakingMicroMoves.headTiltAt(step)
            assert(tilt in -4..4) { "tilt $tilt at step $step" }
        }
    }

    @Test
    fun headRollAt_periodicSubtleRoll() {
        assertEquals(2, SpeakingMicroMoves.headRollAt(3))
        assertNull(SpeakingMicroMoves.headRollAt(0))
    }
}
