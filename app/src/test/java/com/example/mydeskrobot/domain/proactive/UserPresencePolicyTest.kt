package com.example.mydeskrobot.domain.proactive

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPresencePolicyTest {

    @Test
    fun `hasRecentUserTurn true inside window`() {
        val now = 1_000_000L
        assertTrue(UserPresencePolicy.hasRecentUserTurn(now - 60_000L, now, windowMinutes = 10))
    }

    @Test
    fun `hasRecentUserTurn false outside window`() {
        val now = 1_000_000L
        assertFalse(UserPresencePolicy.hasRecentUserTurn(now - 11 * 60_000L, now, windowMinutes = 10))
    }

    @Test
    fun `predictivityPresentEnough uses recent turn without body`() = runTest {
        val now = 1_000_000L
        val present = UserPresencePolicy.predictivityPresentEnough(
            lastUserTurnMs = now - 60_000L,
            bodyConfigured = false,
            bodyReachable = false,
            locateUser = { false },
            now = now,
        )
        assertTrue(present)
    }

    @Test
    fun `predictivityPresentEnough uses body locate when no recent turn`() = runTest {
        val now = 1_000_000L
        var locateCalled = false
        val present = UserPresencePolicy.predictivityPresentEnough(
            lastUserTurnMs = null,
            bodyConfigured = true,
            bodyReachable = true,
            locateUser = {
                locateCalled = true
                true
            },
            now = now,
        )
        assertTrue(present)
        assertTrue(locateCalled)
    }

    @Test
    fun `predictivityPresentEnough false without turn or face`() = runTest {
        val now = 1_000_000L
        val present = UserPresencePolicy.predictivityPresentEnough(
            lastUserTurnMs = null,
            bodyConfigured = true,
            bodyReachable = true,
            locateUser = { false },
            now = now,
        )
        assertFalse(present)
    }

    @Test
    fun `wellnessPresentEnough with body prefers locate before turn`() = runTest {
        val now = 1_000_000L
        var locateCalled = false
        val present = UserPresencePolicy.wellnessPresentEnough(
            lastUserTurnMs = null,
            bodyConfigured = true,
            bodyReachable = true,
            locateUser = {
                locateCalled = true
                true
            },
            presenceWindowMinutes = 5,
            now = now,
        )
        assertTrue(present)
        assertTrue(locateCalled)
    }

    @Test
    fun `wellnessPresentEnough with body falls back to recent turn when locate fails`() = runTest {
        val now = 1_000_000L
        val present = UserPresencePolicy.wellnessPresentEnough(
            lastUserTurnMs = now - 3 * 60_000L,
            bodyConfigured = true,
            bodyReachable = true,
            locateUser = { false },
            presenceWindowMinutes = 5,
            now = now,
        )
        assertTrue(present)
    }

    @Test
    fun `wellnessPresentEnough with body false when locate fails and turn stale`() = runTest {
        val now = 1_000_000L
        val present = UserPresencePolicy.wellnessPresentEnough(
            lastUserTurnMs = now - 10 * 60_000L,
            bodyConfigured = true,
            bodyReachable = true,
            locateUser = { false },
            presenceWindowMinutes = 5,
            now = now,
        )
        assertFalse(present)
    }

    @Test
    fun `wellnessPresentEnough without body uses only recent turn`() = runTest {
        val now = 1_000_000L
        assertTrue(
            UserPresencePolicy.wellnessPresentEnough(
                lastUserTurnMs = now - 2 * 60_000L,
                bodyConfigured = false,
                bodyReachable = false,
                locateUser = { true },
                presenceWindowMinutes = 5,
                now = now,
            ),
        )
        assertFalse(
            UserPresencePolicy.wellnessPresentEnough(
                lastUserTurnMs = now - 10 * 60_000L,
                bodyConfigured = false,
                bodyReachable = false,
                locateUser = { true },
                presenceWindowMinutes = 5,
                now = now,
            ),
        )
    }
}
