package com.example.mydeskrobot.data.presence

import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Latest face-in-frame offset from [com.example.mydeskrobot.integration.presence.DeskPresenceMonitor].
 * Read on attention triggers only — not used for continuous tracking.
 */
object FaceGazeStateStore {
    private val _gaze = MutableStateFlow<FaceGazeSnapshot?>(null)
    val gaze: StateFlow<FaceGazeSnapshot?> = _gaze.asStateFlow()

    fun update(snapshot: FaceGazeSnapshot?) {
        _gaze.value = snapshot
    }

    fun current(): FaceGazeSnapshot? = _gaze.value

    fun reset() {
        _gaze.value = null
    }
}
