package com.example.mydeskrobot.domain.pending

import org.junit.Assert.assertEquals
import org.junit.Test

class UnannouncedNotificationTest {

    @Test
    fun displayBody_prefersRobotSummary() {
        val notification = UnannouncedNotification(
            id = "1",
            appLabel = "WhatsApp",
            title = "Mario",
            text = "Ci vediamo?",
            packageName = "com.whatsapp",
            receivedAtMillis = 1L,
            dedupKey = "key",
            robotSummary = "Messaggio da Mario: ci vediamo alle 18.",
        )

        assertEquals("Messaggio da Mario: ci vediamo alle 18.", notification.displayBody())
    }

    @Test
    fun displayBody_fallsBackToTitleAndText() {
        val notification = UnannouncedNotification(
            id = "1",
            appLabel = "WhatsApp",
            title = "Mario",
            text = "Ci vediamo?",
            packageName = "com.whatsapp",
            receivedAtMillis = 1L,
            dedupKey = "key",
        )

        assertEquals("Mario — Ci vediamo?", notification.displayBody())
    }
}
