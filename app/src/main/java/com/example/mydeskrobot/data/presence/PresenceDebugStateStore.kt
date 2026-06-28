package com.example.mydeskrobot.data.presence

import com.example.mydeskrobot.domain.presence.PresenceDebugFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Latest ML Kit frame geometry for the presence debug overlay (settings / QA).
 */
object PresenceDebugStateStore {
    private val _frame = MutableStateFlow<PresenceDebugFrame?>(null)
    val frame: StateFlow<PresenceDebugFrame?> = _frame.asStateFlow()

    fun update(frame: PresenceDebugFrame?) {
        _frame.value = frame
    }

    fun reset() {
        _frame.value = null
    }
}
