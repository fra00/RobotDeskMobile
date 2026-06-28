package com.example.mydeskrobot.data.presence

import com.example.mydeskrobot.domain.presence.DeskOccupancy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide holder for the latest desk occupancy reading.
 * Updated by [com.example.mydeskrobot.integration.presence.DeskPresenceMonitor].
 */
object DeskPresenceStateStore {
    private val _occupancy = MutableStateFlow(DeskOccupancy.UNKNOWN)
    val occupancy: StateFlow<DeskOccupancy> = _occupancy.asStateFlow()

    fun update(occupancy: DeskOccupancy) {
        _occupancy.value = occupancy
    }

    fun current(): DeskOccupancy = _occupancy.value

    fun reset() {
        _occupancy.value = DeskOccupancy.UNKNOWN
    }
}
