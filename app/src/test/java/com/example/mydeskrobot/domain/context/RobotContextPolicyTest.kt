package com.example.mydeskrobot.domain.context

import com.example.mydeskrobot.reasoning.model.NotificationMode
import com.example.mydeskrobot.reasoning.model.RobotContextState
import com.example.mydeskrobot.reasoning.model.RobotProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RobotContextPolicyTest {

    @Test
    fun `shouldSuppressNotificationTts when profile is CALL`() {
        val state = RobotContextState(profile = RobotProfile.CALL, notificationMode = NotificationMode.SILENT)
        assertTrue(RobotContextPolicy.shouldSuppressNotificationTts(state))
    }

    @Test
    fun `shouldNotSuppressTts when NORMAL`() {
        assertFalse(RobotContextPolicy.shouldSuppressNotificationTts(RobotContextState.NORMAL))
    }

    @Test
    fun `resolveEffectiveState returns NORMAL after validUntil`() {
        val past = System.currentTimeMillis() - 60_000
        val state = RobotContextState(
            profile = RobotProfile.CALL,
            notificationMode = NotificationMode.SILENT,
            validUntilEpochMs = past,
        )
        val effective = RobotContextPolicy.resolveEffectiveState(state)
        assertTrue(effective.isNormal)
        assertFalse(RobotContextPolicy.shouldDropNotifications(state))
    }

    @Test
    fun `resolveEffectiveState returns NORMAL outside daily window`() {
        val cal = Calendar.getInstance()
        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = (nowMinutes + 120) % (24 * 60)
        val end = (nowMinutes + 180) % (24 * 60)

        val state = RobotContextState(
            profile = RobotProfile.MEETING,
            notificationMode = NotificationMode.SILENT,
            windowStartMinutes = start,
            windowEndMinutes = end,
        )
        val effective = RobotContextPolicy.resolveEffectiveState(state)
        assertTrue(effective.isNormal)
    }

    @Test
    fun `shouldClearOnSessionEnd for session-only notification silence`() {
        val state = RobotContextState(
            profile = RobotProfile.NORMAL,
            notificationMode = NotificationMode.SILENT,
            sessionOnly = true,
        )
        assertTrue(RobotContextPolicy.shouldClearOnSessionEnd(state))
    }

    @Test
    fun `shouldNotClearOnSessionEnd for work profile even with sessionOnly`() {
        val state = RobotContextState(
            profile = RobotProfile.WORK,
            notificationMode = NotificationMode.SILENT,
            sessionOnly = true,
        )
        assertFalse(RobotContextPolicy.shouldClearOnSessionEnd(state))
    }

    @Test
    fun `shouldNotClearOnSessionEnd for call profile`() {
        val state = RobotContextState(
            profile = RobotProfile.CALL,
            notificationMode = NotificationMode.SILENT,
            sessionOnly = false,
        )
        assertFalse(RobotContextPolicy.shouldClearOnSessionEnd(state))
    }

    @Test
    fun `buildPromptSection empty for NORMAL`() {
        assertTrue(RobotContextPolicy.buildPromptSection(RobotContextState.NORMAL).isBlank())
    }

    @Test
    fun `buildPromptSection includes profile for WORK`() {
        val section = RobotContextPolicy.buildPromptSection(
            RobotContextState(profile = RobotProfile.WORK, notificationMode = NotificationMode.SILENT),
        )
        assertTrue(section.contains("WORK"))
        assertTrue(section.contains("processed silently"))
    }
}
