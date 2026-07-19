package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.heartbeat.HeartbeatSettings
import com.example.mydeskrobot.data.proactive.ProactivitySettings
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WellnessWatcherTest {

    private val anchorMs = 1_000_000L
    private val nowMs = anchorMs + 65 * 60_000L

    private val watcher = WellnessWatcher(nowMillis = { nowMs })

    @Test
    fun `nextPhase returns domain score when gates pass without body`() {
        val phase = watcher.nextPhase(baseContext(bodyConfigured = false))
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    @Test
    fun `nextPhase returns visual first when body available and order enabled`() {
        val phase = watcher.nextPhase(baseContext(bodyConfigured = true, bodyReachable = true))
        assertEquals(WellnessPhase.VISUAL_ORDER, phase)
    }

    @Test
    fun `nextPhase skips visual when order domain disabled`() {
        val phase = watcher.nextPhase(
            baseContext(
                bodyConfigured = true,
                bodyReachable = true,
                enabledDomainIds = setOf("pasti", "carico_lavoro"),
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    @Test
    fun `nextPhase null when all care domains disabled`() {
        assertNull(watcher.nextPhase(baseContext(enabledDomainIds = emptySet())))
    }

    @Test
    fun `nextPhase null before anchor elapsed`() {
        val earlyWatcher = WellnessWatcher(nowMillis = { anchorMs + 30 * 60_000L })
        assertNull(earlyWatcher.nextPhase(baseContext()))
    }

    @Test
    fun `nextPhase null when wellness disabled`() {
        assertNull(
            watcher.nextPhase(
                baseContext(proactivity = ProactivitySettings(wellnessEnabled = false)),
            ),
        )
    }

    @Test
    fun `nextPhase null when check already done today`() {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(anchorMs + 50 * 60_000L)
            .withWellnessCheckDone()
        assertNull(watcher.nextPhase(baseContext(workingMemory = wm)))
    }

    @Test
    fun `nextPhase null on silent robot context`() {
        val context = baseContext(
            robotContext = RobotContextState(
                profile = RobotProfile.WORK,
                notificationMode = NotificationMode.SILENT,
            ),
        )
        assertNull(watcher.nextPhase(context))
    }

    @Test
    fun `nextPhase null when idle below threshold`() {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 3 * 60_000L)
        assertNull(
            watcher.nextPhase(
                baseContext(
                    workingMemory = wm,
                    proactivity = ProactivitySettings(wellnessIdleMinutes = 5),
                ),
            ),
        )
    }

    @Test
    fun `nextPhase null when mic session inactive`() {
        assertNull(watcher.nextPhase(baseContext(micSessionActive = false)))
    }

    @Test
    fun `nextPhase null in night mode`() {
        assertNull(watcher.nextPhase(baseContext(isNightMode = true)))
    }

    @Test
    fun `nextPhase skips visual when already done today`() {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(anchorMs + 30 * 60_000L)
            .withWellnessVisualDone()
        val phase = watcher.nextPhase(
            baseContext(
                workingMemory = wm,
                bodyConfigured = true,
                bodyReachable = true,
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    @Test
    fun `nextPhase null when heartbeat disabled`() {
        assertNull(
            watcher.nextPhase(
                baseContext(heartbeatSettings = HeartbeatSettings(enabled = false)),
            ),
        )
    }

    private fun baseContext(
        workingMemory: WorkingMemory = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(anchorMs + 30 * 60_000L),
        bodyConfigured: Boolean = false,
        bodyReachable: Boolean = false,
        proactivity: ProactivitySettings = ProactivitySettings(),
        robotContext: RobotContextState? = null,
        micSessionActive: Boolean = true,
        isNightMode: Boolean = false,
        heartbeatSettings: HeartbeatSettings = HeartbeatSettings(enabled = true),
        enabledDomainIds: Set<String> = com.example.mydeskrobot.domain.wellness.WellnessDomains.ALL,
    ) = WellnessWatchContext(
        heartbeatSettings = heartbeatSettings,
        proactivitySettings = proactivity,
        workingMemory = workingMemory,
        robotContext = robotContext,
        micSessionActive = micSessionActive,
        bodyConfigured = bodyConfigured,
        bodyReachable = bodyReachable,
        llmBusy = false,
        isNightMode = isNightMode,
        enabledDomainIds = enabledDomainIds,
    )
}
