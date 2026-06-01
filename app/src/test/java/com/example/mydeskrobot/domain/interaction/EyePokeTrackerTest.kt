package com.example.mydeskrobot.domain.interaction

import com.example.mydeskrobot.domain.model.RobotEmotion
import org.junit.Assert.assertEquals
import org.junit.Test

class EyePokeTrackerTest {

    @Test
    fun firstPoke_isMildlyAnnoyed() {
        val tracker = EyePokeTracker()
        val reaction = tracker.recordPoke(0L)
        assertEquals(RobotEmotion.CONFUSED, reaction.emotion)
        assertEquals(1, reaction.tier)
    }

    @Test
    fun thirdPoke_becomesAngry() {
        val tracker = EyePokeTracker()
        tracker.recordPoke(0L)
        tracker.recordPoke(1_000L)
        val reaction = tracker.recordPoke(2_000L)
        assertEquals(RobotEmotion.ANGRY, reaction.emotion)
        assertEquals(2, reaction.tier)
    }

    @Test
    fun sixthPoke_maxAngerTier() {
        val tracker = EyePokeTracker()
        repeat(5) { i -> tracker.recordPoke(i * 1_000L) }
        val reaction = tracker.recordPoke(5_000L)
        assertEquals(RobotEmotion.ANGRY, reaction.emotion)
        assertEquals(3, reaction.tier)
    }

    @Test
    fun oldPokesExpireOutsideWindow() {
        val tracker = EyePokeTracker(windowMs = 10_000L)
        tracker.recordPoke(0L)
        assertEquals(0, tracker.recentPokeCount(11_000L))
        val reaction = tracker.recordPoke(11_000L)
        assertEquals(1, tracker.recentPokeCount(11_000L))
        assertEquals(RobotEmotion.CONFUSED, reaction.emotion)
    }
}
