package com.example.mydeskrobot.data.check

import org.junit.Assert.assertEquals
import org.junit.Test

class FireAndCheckRepositoryTest {

    @Test
    fun cancelledLoopReminders_dedupesIds() {
        val ids = FireAndCheckRepository.CancelledLoopReminders(
            primaryReminderId = 10L,
            verificationReminderId = 10L,
        ).reminderIds()
        assertEquals(listOf(10L), ids)
    }

    @Test
    fun cancelledLoopReminders_includesBothWhenDistinct() {
        val ids = FireAndCheckRepository.CancelledLoopReminders(
            primaryReminderId = 10L,
            verificationReminderId = 11L,
        ).reminderIds()
        assertEquals(listOf(10L, 11L), ids)
    }
}
