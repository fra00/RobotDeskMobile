package com.example.mydeskrobot.data.presence

import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Latest face-in-frame offset from on-demand [com.example.mydeskrobot.integration.presence.DeskPresenceMonitor] sessions.
 * Tracks [lastFaceSeenAtMs] across brief absences (null gaze) until [reset].
 * Stopping a camera session clears live gaze via [update](null) but **preserves** [lastFaceSeenAtMs]
 * so idle re-acquire can schedule the next sample.
 */
object FaceGazeStateStore {
    private val _gaze = MutableStateFlow<FaceGazeSnapshot?>(null)
    val gaze: StateFlow<FaceGazeSnapshot?> = _gaze.asStateFlow()

    @Volatile
    private var lastFaceSeenAtMs: Long? = null

    fun update(snapshot: FaceGazeSnapshot?) {
        if (snapshot != null) {
            lastFaceSeenAtMs = snapshot.capturedAt
        }
        _gaze.value = snapshot
    }

    fun current(): FaceGazeSnapshot? = _gaze.value

    fun lastFaceSeenAtMs(): Long? = lastFaceSeenAtMs

    /** Null if a face was never seen since [reset]. */
    fun millisSinceLastFace(now: Long = System.currentTimeMillis()): Long? {
        val seenAt = lastFaceSeenAtMs ?: return null
        return (now - seenAt).coerceAtLeast(0L)
    }

    fun reset() {
        _gaze.value = null
        lastFaceSeenAtMs = null
    }
}
