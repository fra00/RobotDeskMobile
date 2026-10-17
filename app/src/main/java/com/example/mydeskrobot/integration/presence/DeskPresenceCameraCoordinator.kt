package com.example.mydeskrobot.integration.presence

import com.example.mydeskrobot.domain.presence.DeskOccupancy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge so vision/centering paths can wake desk-presence analysis
 * without holding a [DeskPresenceMonitor] reference (e.g. [UserAttentionCentering]
 * built inside [com.example.mydeskrobot.integration.ReasoningModule]).
 *
 * Idle default: unbound or session depth 0 → camera off.
 */
object DeskPresenceCameraCoordinator {

    @Volatile
    private var session: DeskPresenceCameraSession? = null

    private val _cameraActive = MutableStateFlow(false)
    val cameraActive: StateFlow<Boolean> = _cameraActive.asStateFlow()

    fun bind(session: DeskPresenceCameraSession) {
        this.session = session
    }

    fun unbind(session: DeskPresenceCameraSession) {
        if (this.session === session) {
            this.session = null
            _cameraActive.value = false
        }
    }

    internal fun publishCameraActive(active: Boolean) {
        _cameraActive.value = active
    }

    suspend fun <T> withCamera(block: suspend () -> T): T {
        val s = session
        return if (s != null) s.withCamera(block) else block()
    }

    suspend fun awaitOccupancySample(timeoutMs: Long = DEFAULT_SAMPLE_TIMEOUT_MS): DeskOccupancy? =
        session?.awaitOccupancySample(timeoutMs)

    const val DEFAULT_SAMPLE_TIMEOUT_MS = 3_000L
}

/**
 * On-demand CameraX + ML Kit analysis session (refcount-aware).
 */
interface DeskPresenceCameraSession {
    suspend fun <T> withCamera(block: suspend () -> T): T
    suspend fun awaitOccupancySample(timeoutMs: Long = DeskPresenceCameraCoordinator.DEFAULT_SAMPLE_TIMEOUT_MS): DeskOccupancy
}
