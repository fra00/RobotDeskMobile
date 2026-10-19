package com.example.mydeskrobot.domain.activitylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkCommunicationChannelsTest {

    @Test
    fun `teams and slack packages are work`() {
        assertTrue(WorkCommunicationChannels.isWorkPackage("com.microsoft.teams"))
        assertTrue(WorkCommunicationChannels.isWorkPackage("com.Slack"))
        assertFalse(WorkCommunicationChannels.isWorkPackage("com.whatsapp"))
        assertFalse(WorkCommunicationChannels.isWorkPackage(null))
    }

    @Test
    fun `channel labels recognize Teams and Slack`() {
        assertTrue(WorkCommunicationChannels.isWorkChannelLabel("Microsoft Teams"))
        assertTrue(WorkCommunicationChannels.isWorkChannelLabel("Slack"))
        assertFalse(WorkCommunicationChannels.isWorkChannelLabel("WhatsApp"))
        assertFalse(WorkCommunicationChannels.isWorkChannelLabel("Telegram"))
    }

    @Test
    fun `episodeKindForNotification routes packages`() {
        assertEquals(
            EpisodeKind.WORK_THREAD,
            WorkCommunicationChannels.episodeKindForNotification(
                packageName = "com.microsoft.teams",
                appLabel = "Microsoft Teams",
            ),
        )
        assertEquals(
            EpisodeKind.SOCIAL_THREAD,
            WorkCommunicationChannels.episodeKindForNotification(
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
            ),
        )
    }

    @Test
    fun `coerceExtractedKind upgrades social mistagged as work channel`() {
        assertEquals(
            EpisodeKind.WORK_THREAD,
            WorkCommunicationChannels.coerceExtractedKind(
                kind = EpisodeKind.SOCIAL_THREAD,
                sourceChannel = "Microsoft Teams",
            ),
        )
        assertEquals(
            EpisodeKind.SOCIAL_THREAD,
            WorkCommunicationChannels.coerceExtractedKind(
                kind = EpisodeKind.SOCIAL_THREAD,
                sourceChannel = "WhatsApp",
            ),
        )
        assertEquals(
            EpisodeKind.PLAN,
            WorkCommunicationChannels.coerceExtractedKind(
                kind = EpisodeKind.PLAN,
                sourceChannel = "Microsoft Teams",
            ),
        )
    }
}
