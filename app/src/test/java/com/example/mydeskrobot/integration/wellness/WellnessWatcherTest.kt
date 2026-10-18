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

/**
 * Wellness v2 compute gates: enabled, mic session, LLM idle, not night, translation off,
 * daily anchor, once/day. Idle buffer / presence / SILENT do **not** block compute.
 */
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
        val earlyWatcher = WellnessWatcher(nowMillis = { anchorMs + 10 * 60_000L })
        assertNull(
            earlyWatcher.nextPhase(
                baseContext(proactivity = ProactivitySettings(wellnessAnchorMinutes = 15)),
            ),
        )
    }

    @Test
    fun `nextPhase ok after default 15 min anchor`() = runTest {
        val midWatcher = WellnessWatcher(nowMillis = { anchorMs + 16 * 60_000L })
        val phase = midWatcher.nextPhase(
            baseContext(proactivity = ProactivitySettings(wellnessAnchorMinutes = 15)),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
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
            .withWellnessCheckDone()
        assertNull(watcher.nextPhase(baseContext(workingMemory = wm)))
    }

    @Test
    fun `nextPhase proceeds on silent robot context — SILENT does not block compute`() = runTest {
        val context = baseContext(
            robotContext = RobotContextState(
                profile = RobotProfile.WORK,
                notificationMode = NotificationMode.SILENT,
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, watcher.nextPhase(context))
    }

    @Test
    fun `nextPhase null when translation mode active`() = runTest {
        assertNull(watcher.nextPhase(baseContext(translationModeActive = true)))
    }

    @Test
    fun `nextPhase ok despite recent user turn — idle buffer dropped in v2`() = runTest {
        val wm = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs)
            .withUserTurn(nowMs - 30_000L)
        val phase = watcher.nextPhase(
            baseContext(
                workingMemory = wm,
                proactivity = ProactivitySettings(wellnessIdleMinutes = 5),
            ),
        )
        assertEquals(WellnessPhase.DOMAIN_SCORE, phase)
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
    fun `nextPhase proceeds when body misses — presence dropped in v2`() = runTest {
        val phase = watcher.nextPhase(
            baseContext(
                bodyConfigured = true,
                bodyReachable = true,
                locateUser = { false },
            ),
        )
        assertEquals(WellnessPhase.VISUAL_ORDER, phase)
    }

    @Test
    fun `nextPhase null when llm busy`() = runTest {
        assertNull(watcher.nextPhase(baseContext(llmBusy = true)))
    }

    private fun baseContext(
        workingMemory: WorkingMemory = WorkingMemory.forToday()
            .withFirstHotwordOn(anchorMs),
        bodyConfigured: Boolean = false,
        bodyReachable: Boolean = false,
        proactivity: ProactivitySettings = ProactivitySettings(wellnessAnchorMinutes = 15),
        robotContext: RobotContextState? = null,
        micSessionActive: Boolean = true,
        isNightMode: Boolean = false,
        translationModeActive: Boolean = false,
        llmBusy: Boolean = false,
        enabledDomainIds: Set<String> = com.example.mydeskrobot.domain.wellness.WellnessDomains.ALL,
        locateUser: suspend () -> Boolean = { false },
    ) = WellnessWatchContext(
        proactivitySettings = proactivity,
        workingMemory = workingMemory,
        robotContext = robotContext,
        micSessionActive = micSessionActive,
        bodyConfigured = bodyConfigured,
        bodyReachable = bodyReachable,
        llmBusy = llmBusy,
        isNightMode = isNightMode,
        translationModeActive = translationModeActive,
        enabledDomainIds = enabledDomainIds,
        locateUser = locateUser,
    )
}
