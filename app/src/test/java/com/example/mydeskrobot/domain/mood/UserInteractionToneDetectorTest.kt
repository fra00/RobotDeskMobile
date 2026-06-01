package com.example.mydeskrobot.domain.mood

import org.junit.Assert.assertEquals
import org.junit.Test

class UserInteractionToneDetectorTest {

    @Test
    fun `detects apology`() {
        assertEquals(
            UserInteractionTone.APOLOGY,
            UserInteractionToneDetector.detect("Scusa, non lo faccio più"),
        )
    }

    @Test
    fun `detects positive`() {
        assertEquals(
            UserInteractionTone.POSITIVE,
            UserInteractionToneDetector.detect("Grazie, sei fantastico"),
        )
    }

    @Test
    fun `neutral question`() {
        assertEquals(
            UserInteractionTone.NEUTRAL,
            UserInteractionToneDetector.detect("Come stai?"),
        )
    }
}
