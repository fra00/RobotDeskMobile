package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.proactive.ProactivitySettings
import com.example.mydeskrobot.domain.memory.WorkingMemory
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WellnessWatcherTest {

    private val anchorMs = 1_000_000L
    private val nowMs = anchorMs + 65 * 60_000L

    private val watcher = WellnessWatcher(nowMillis = { nowMs })

    @Test
    fun `nextPhase returns domain score when gates pass without body`() = runTest {
        val phase = watcher.nextPhase(baseContext(bodyConfigured = false))
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    @Test
    fun `nextPhase returns visual first when body available and order enabled`() = runTest {
        val phase = watcher.nextPhase(
            baseContext(
                bodyConfigured = true,
                bodyReachable = true,
                locateUser = { true },
            ),
        )
        assertEquals(WellnessPhase.VISUAL_ORDER, phase)
    }

    @Test
    fun `nextPhase skips visual when order domain disabled`() = runTest {
        val phase = watcher.nextPhase(
            baseContext(
                bodyConfigured = true,
                bodyReachable = true,
                locateUser = { true },
                enabledDomainIds = setOf("pasti", "carico_lavoro"),
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    @Test
    fun `nextPhase null when all care domains disabled`() = runTest {
        assertNull(watcher.nextPhase(baseContext(enabledDomainIds = emptySet())))
    }

    @Test
    fun `nextPhase null before anchor elapsed`() = runTest {
        val earlyWatcher = WellnessWatcher(nowMillis = { anchorMs + 30 * 60_000L })
        assertNull(earlyWatcher.nextPhase(baseContext()))
    }

    @Test
    fun `nextPhase null when wellness disabled`() = runTest {
        assertNull(
            watcher.nextPhase(
                baseContext(proactivity = ProactivitySettings(wellnessEnabled = false)),
            ),
        )
    }

    @Test
    fun `nextPhase null when check already done today`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(anchorMs + 50 * 60_000L)
            .withWellnessCheckDone()
        assertNull(watcher.nextPhase(baseContext(workingMemory = wm)))
    }

    @Test
    fun `nextPhase null on silent robot context`() = runTest {
        val context = baseContext(
            robotContext = RobotContextState(
                profile = RobotProfile.WORK,
                notificationMode = NotificationMode.SILENT,
            ),
        )
        assertNull(watcher.nextPhase(context))
    }

    @Test
    fun `nextPhase null when idle below threshold`() = runTest {
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
    fun `nextPhase null when mic session inactive`() = runTest {
        assertNull(watcher.nextPhase(baseContext(micSessionActive = false)))
    }

    @Test
    fun `nextPhase null in night mode`() = runTest {
        assertNull(watcher.nextPhase(baseContext(isNightMode = true)))
    }

    @Test
    fun `nextPhase skips visual when already done today`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 8 * 60_000L)
            .withWellnessVisualDone()
        val phase = watcher.nextPhase(
            baseContext(
                workingMemory = wm,
                bodyConfigured = true,
                bodyReachable = true,
                locateUser = { true },
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    @Test
    fun `nextPhase null when body misses and no recent interaction`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 30 * 60_000L)
        assertNull(
            watcher.nextPhase(
                baseContext(
                    workingMemory = wm,
                    bodyConfigured = true,
                    bodyReachable = true,
                    locateUser = { false },
                    proactivity = ProactivitySettings(
                        wellnessIdleMinutes = 5,
                        wellnessPresenceMinutes = 5,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `nextPhase proceeds when body misses but recent interaction within presence window`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 4 * 60_000L)
        // idle buffer would block at 5 — use idle=3 so presence is the focus
        val phase = watcher.nextPhase(
            baseContext(
                workingMemory = wm,
                bodyConfigured = true,
                bodyReachable = true,
                locateUser = { false },
                proactivity = ProactivitySettings(
                    wellnessIdleMinutes = 3,
                    wellnessPresenceMinutes = 5,
                ),
            ),
        )
        assertEquals(WellnessPhase.VISUAL_ORDER, phase)
    }

    @Test
    fun `nextPhase null without body when last turn outside presence window`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 20 * 60_000L)
        assertNull(
            watcher.nextPhase(
                baseContext(
                    workingMemory = wm,
                    bodyConfigured = false,
                    proactivity = ProactivitySettings(
                        wellnessIdleMinutes = 5,
                        wellnessPresenceMinutes = 15,
                    ),
                ),
            ),
        )
    }

    @Test
    fun `nextPhase without body ok when turn after idle and inside presence`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 8 * 60_000L)
        val phase = watcher.nextPhase(
            baseContext(
                workingMemory = wm,
                bodyConfigured = false,
                proactivity = ProactivitySettings(
                    wellnessIdleMinutes = 5,
                    wellnessPresenceMinutes = 15,
                ),
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
    }

    private fun baseContext(
        workingMemory: WorkingMemory = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 8 * 60_000L),
        bodyConfigured: Boolean = false,
        bodyReachable: Boolean = false,
        proactivity: ProactivitySettings = ProactivitySettings(
            wellnessIdleMinutes = 5,
            wellnessPresenceMinutes = 15,
        ),
        robotContext: RobotContextState? = null,
        micSessionActive: Boolean = true,
        isNightMode: Boolean = false,
        enabledDomainIds: Set<String> = com.example.mydeskrobot.domain.wellness.WellnessDomains.ALL,
        locateUser: suspend () -> Boolean = { false },
    ) = WellnessWatchContext(
        proactivitySettings = proactivity,
        workingMemory = workingMemory,
        robotContext = robotContext,
        micSessionActive = micSessionActive,
        bodyConfigured = bodyConfigured,
        bodyReachable = bodyReachable,
        llmBusy = false,
        isNightMode = isNightMode,
        enabledDomainIds = enabledDomainIds,
        locateUser = locateUser,
    )
}
