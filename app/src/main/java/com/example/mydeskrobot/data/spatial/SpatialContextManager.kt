package com.example.mydeskrobot.data.spatial

import com.example.mydeskrobot.domain.spatial.RoomType
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.domain.spatial.SpatialResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * SSOT for runtime spatial context (current room at desk).
 */
class SpatialContextManager(
    private val placeRepository: SpatialPlaceRepository,
    private val contextRepository: SpatialContextRepository,
    private val scope: CoroutineScope,
) {
    private val _snapshot = MutableStateFlow(SpatialContextSnapshot())
    val snapshot: StateFlow<SpatialContextSnapshot> = _snapshot.asStateFlow()

    suspend fun initialize() {
        _snapshot.value = contextRepository.load()
    }

    fun invalidateCurrentPlace() {
        scope.launch {
            contextRepository.invalidate()
            _snapshot.value = SpatialContextSnapshot(resolution = SpatialResolution.UNKNOWN)
        }
    }

    fun setCurrentPlace(
        placeId: Long?,
        label: String?,
        confidence: Float,
        resolution: SpatialResolution,
        landmarks: List<String> = emptyList(),
        roomType: RoomType? = null,
    ) {
        scope.launch {
            if (placeId != null) {
                placeRepository.touchLastSeen(placeId)
            }
            val snapshot = SpatialContextSnapshot(
                currentPlaceId = placeId,
                currentPlaceLabel = label,
                roomType = roomType,
                confidence = confidence.coerceIn(0f, 1f),
                resolution = resolution,
                lastLandmarks = landmarks,
            )
            contextRepository.save(snapshot)
            _snapshot.value = snapshot
        }
    }

    fun updateLastScan(landmarks: List<String>) {
        scope.launch {
            val current = _snapshot.value
            val updated = current.copy(lastLandmarks = landmarks)
            contextRepository.save(updated)
            _snapshot.value = updated
        }
    }

    suspend fun knownPlaceLabels(): List<String> =
        placeRepository.labelSummaries()
}
