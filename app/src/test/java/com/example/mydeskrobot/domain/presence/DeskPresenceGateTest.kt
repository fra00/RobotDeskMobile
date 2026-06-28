package com.example.mydeskrobot.domain.presence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskPresenceGateTest {

    @Test
    fun allows_present() {
        assertTrue(
            DeskPresenceGate.allowsProactiveInteraction(
                occupancy = DeskOccupancy(state = DeskOccupancyState.PRESENT),
                lastInteractionMillis = 0L,
                monitorEnabled = true,
            ),
        )
    }

    @Test
    fun blocks_absent() {
        assertFalse(
            DeskPresenceGate.allowsProactiveInteraction(
                occupancy = DeskOccupancy(state = DeskOccupancyState.ABSENT),
                lastInteractionMillis = System.currentTimeMillis(),
                monitorEnabled = true,
            ),
        )
    }

    @Test
    fun monitor_disabled_allows_all() {
        assertTrue(
            DeskPresenceGate.allowsProactiveInteraction(
                occupancy = DeskOccupancy(state = DeskOccupancyState.ABSENT),
                lastInteractionMillis = 0L,
                monitorEnabled = false,
            ),
        )
    }
}
