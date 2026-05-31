package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import org.junit.Assert.*
import org.junit.Test

class HeartbeatInputSourceTest {

    private val source = HeartbeatInputSource()

    @Test
    fun `id is heartbeat`() {
        assertEquals("heartbeat", source.id)
    }

    @Test
    fun `priority is deferred`() {
        assertEquals(InputPriority.DEFERRED, source.priority)
    }

    @Test
    fun `isEnabled returns true`() {
        assertTrue(source.isEnabled())
    }

    @Test
    fun `normalize returns input if Heartbeat`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 14,
            currentMinute = 30,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 1,
            relevantRoutines = listOf("Pranzo alle 13"),
        )

        val result = source.normalize(heartbeat)

        assertEquals(heartbeat, result)
    }

    @Test
    fun `normalize returns null for non-Heartbeat`() {
        val notification = RobotInput.Notification(
            packageName = "com.test",
            appLabel = "Test",
            title = "Title",
            text = "Text",
            notificationKey = "key",
        )

        val result = source.normalize(notification)

        assertNull(result)
    }

    @Test
    fun `shouldAccept returns true for Heartbeat`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 14,
            currentMinute = 30,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
        )

        assertTrue(source.shouldAccept(heartbeat))
    }

    @Test
    fun `toEnvelope formats correctly`() {
        val heartbeat = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 20,
            currentHour = 9,
            currentMinute = 5,
            dayOfWeek = "martedì",
            pendingRemindersCount = 2,
            relevantRoutines = listOf("Caffè mattutino"),
            timestamp = 1234567890000L,
        )

        val envelope = source.toEnvelope(heartbeat)

        assertTrue(envelope.formattedForLlm.contains("[SYSTEM_INPUT: heartbeat]"))
        assertTrue(envelope.formattedForLlm.contains("Ora: 9:05"))
        assertTrue(envelope.formattedForLlm.contains("Giorno: martedì"))
        assertTrue(envelope.formattedForLlm.contains("Minuti dall'ultima interazione: 20"))
        assertTrue(envelope.formattedForLlm.contains("Promemoria attivi: 2"))
        assertTrue(envelope.formattedForLlm.contains("Routine: Caffè mattutino"))
        assertTrue(envelope.dedupKey.startsWith("heartbeat:"))
    }

    @Test
    fun `toDedupKey uses minute bucket`() {
        val heartbeat1 = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            timestamp = 60000L,
        )
        val heartbeat2 = RobotInput.Heartbeat(
            minutesSinceLastInteraction = 5,
            currentHour = 10,
            currentMinute = 0,
            dayOfWeek = "lunedì",
            pendingRemindersCount = 0,
            relevantRoutines = emptyList(),
            timestamp = 65000L,
        )

        val key1 = source.toDedupKey(heartbeat1)
        val key2 = source.toDedupKey(heartbeat2)

        assertEquals(key1, key2)
    }
}
