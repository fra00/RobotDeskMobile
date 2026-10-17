package com.example.mydeskrobot.domain.activitylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotSelfActivityFilterTest {

    @Test
    fun `tagLabel adds stable prefix`() {
        assertEquals(
            "[robot] Ha iniziato a giocare a Pong",
            RobotSelfActivityFilter.tagLabel("Ha iniziato a giocare a Pong"),
        )
        assertEquals(
            "[robot] Ha iniziato a giocare a Pong",
            RobotSelfActivityFilter.tagLabel("[robot] Ha iniziato a giocare a Pong"),
        )
    }

    @Test
    fun `svago channel is excluded`() {
        assertTrue(RobotSelfActivityFilter.isRobotSelfChannel("svago"))
        assertTrue(
            RobotSelfActivityFilter.isExcludedSystemEntryText(
                "svago: [robot] Ha iniziato a giocare a Pong",
            ),
        )
    }

    @Test
    fun `whatsapp system line is kept`() {
        assertFalse(
            RobotSelfActivityFilter.isExcludedSystemEntryText(
                "WhatsApp: Mario: ci vediamo stasera?",
            ),
        )
    }

    @Test
    fun `prefixed and legacy robot labels match`() {
        assertTrue(RobotSelfActivityFilter.isRobotSelfActivity("[robot] ascolto musica"))
        assertTrue(RobotSelfActivityFilter.isRobotSelfActivity("Gioco pong"))
        assertTrue(RobotSelfActivityFilter.isRobotSelfActivity("fine sessione pong"))
        assertTrue(RobotSelfActivityFilter.isRobotSelfActivity("ascoltare musica con le cuffie"))
        assertTrue(RobotSelfActivityFilter.isRobotSelfActivity("pong"))
        assertTrue(RobotSelfActivityFilter.isRobotSelfActivity("musica"))
    }

    @Test
    fun `normal user activities are kept`() {
        assertTrue(RobotSelfActivityFilter.isUserFacingEpisode("passeggiata"))
        assertTrue(RobotSelfActivityFilter.isUserFacingEpisode("colazione"))
        assertTrue(RobotSelfActivityFilter.isUserFacingEpisode("tapis roulant"))
        assertTrue(RobotSelfActivityFilter.isUserFacingEpisode("ascolto spotify"))
        assertFalse(RobotSelfActivityFilter.isUserFacingEpisode("[robot] lettura"))
    }
}
