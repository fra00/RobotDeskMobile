package com.example.mydeskrobot.domain.proactive

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.integration.input.heartbeat.ProactiveGatePolicy
import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ProactiveSpeakGateTest {

    private val activeNowMs: Long = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, 11, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `canSpeak proceeds when all gates pass`() {
        val decision = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = HeartbeatSettings(enabled = true),
                workingMemory = WorkingMemory.forToday(),
                robotContext = null,
                nowMs = activeNowMs,
            ),
        )
        assertEquals(GateDecision.Proceed, decision)
    }

    @Test
    fun `canSpeak proceeds when micro-tick switch disabled`() {
        // HeartbeatSettings.enabled gates alarm/micro-tick only — not predictivity speak.
        val decision = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = HeartbeatSettings(enabled = false),
                workingMemory = WorkingMemory.forToday(),
                robotContext = null,
                nowMs = activeNowMs,
            ),
        )
        assertEquals(GateDecision.Proceed, decision)
    }

    @Test
    fun `canSpeak skips on silent robot context`() {
        val decision = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = HeartbeatSettings(enabled = true),
                workingMemory = WorkingMemory.forToday(),
                robotContext = RobotContextState(
                    profile = RobotProfile.WORK,
                    notificationMode = NotificationMode.SILENT,
                ),
                nowMs = activeNowMs,
            ),
        )
        assertTrue(decision is GateDecision.Skip)
        assertEquals("robot context silent", (decision as GateDecision.Skip).reason)
    }

    @Test
    fun `canSpeak skips when daily proactive cap reached`() {
        var wm = WorkingMemory.forToday()
        repeat(ProactiveGatePolicy.MAX_PROACTIVE_SPEAKS_PER_DAY) {
            wm = wm.withProactiveSpeak(activeNowMs - 60_000L)
        }
        val decision = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = HeartbeatSettings(enabled = true),
                workingMemory = wm,
                robotContext = null,
                nowMs = activeNowMs,
            ),
        )
        assertTrue(decision is GateDecision.Skip)
        assertEquals("daily proactive cap", (decision as GateDecision.Skip).reason)
    }

    @Test
    fun `canSpeak skips during proactive cooldown`() {
        val wm = WorkingMemory.forToday().withProactiveSpeak(activeNowMs - 5 * 60_000L)
        val decision = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = HeartbeatSettings(enabled = true),
                workingMemory = wm,
                robotContext = null,
                nowMs = activeNowMs,
            ),
        )
        assertTrue(decision is GateDecision.Skip)
        assertEquals("proactive cooldown", (decision as GateDecision.Skip).reason)
    }

    @Test
    fun `canSpeak skips outside active window`() {
        val nightMs = Calendar.getInstance().apply {
            set(2026, Calendar.JUNE, 11, 3, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val decision = ProactiveSpeakGate.canSpeak(
            ProactiveSpeakContext(
                heartbeatSettings = HeartbeatSettings(enabled = true, startHour = 7, endHour = 23),
                workingMemory = WorkingMemory.forToday(),
                robotContext = null,
                nowMs = nightMs,
            ),
        )
        assertTrue(decision is GateDecision.Skip)
        assertEquals("outside active window", (decision as GateDecision.Skip).reason)
    }
}
