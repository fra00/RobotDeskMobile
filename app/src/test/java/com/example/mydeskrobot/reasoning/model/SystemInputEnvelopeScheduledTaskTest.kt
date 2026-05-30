package com.example.mydeskrobot.reasoning.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemInputEnvelopeScheduledTaskTest {

    @Test
    fun `fromScheduledTask formats LLM block and dedup key`() {
        val input = RobotInput.ScheduledTaskFired(
            taskId = 42L,
            message = "Prendi le medicine",
            triggerAtMillis = 1_700_000_000_000L,
        )

        val envelope = SystemInputEnvelope.fromScheduledTask(input)

        assertTrue(envelope.formattedForLlm.contains("[SYSTEM_INPUT: scheduled_task]"))
        assertTrue(envelope.formattedForLlm.contains("Id: 42"))
        assertTrue(envelope.formattedForLlm.contains("Messaggio: Prendi le medicine"))
        assertEquals("task:42:1700000000000", envelope.dedupKey)
        assertEquals(input, envelope.input)
    }

    @Test
    fun `from dispatches scheduled task`() {
        val input = RobotInput.ScheduledTaskFired(
            taskId = 1L,
            message = "Test",
            triggerAtMillis = System.currentTimeMillis(),
        )
        val envelope = SystemInputEnvelope.from(input)
        assertTrue(envelope.input is RobotInput.ScheduledTaskFired)
    }
}
