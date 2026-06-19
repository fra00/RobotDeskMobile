package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.pending.UnannouncedNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingInboxMapperUnannouncedTest {

    @Test
    fun fromUnannouncedNotifications_mapsToPendingInboxItem() {
        val items = PendingInboxMapper.fromUnannouncedNotifications(
            listOf(
                UnannouncedNotification(
                    id = "abc",
                    appLabel = "WhatsApp",
                    title = "Mario",
                    text = "Ciao",
                    packageName = "com.whatsapp",
                    receivedAtMillis = 100L,
                    dedupKey = "dedup",
                ),
            ),
        )

        assertEquals("unannounced:abc", items.single().id)
        assertEquals("WhatsApp", items.single().title)
        assertEquals("Mario — Ciao", items.single().body)
    }

    @Test
    fun parseUnannouncedId_roundTrips() {
        assertEquals("abc", PendingInboxMapper.parseUnannouncedId("unannounced:abc"))
        assertNull(PendingInboxMapper.parseUnannouncedId("deferred:x"))
    }
}
