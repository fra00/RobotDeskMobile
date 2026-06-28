package com.example.mydeskrobot.domain.presence

import kotlin.math.hypot
import kotlin.math.max

/**
 * Stateful per-frame face gating: minimum bbox size and rejection of perfectly static boxes
 * (posters, photos on wall) that ML Kit mis-detects as faces.
 */
data class FaceFilterInput(
    val trackingId: Int?,
    val box: NormalizedRect,
    val inRoi: Boolean,
    val confidence: Float,
)

class PresenceFaceFilter(
    private val minWidthFraction: Float = MIN_WIDTH_FRACTION,
    private val minHeightFraction: Float = MIN_HEIGHT_FRACTION,
    private val staticObservationMs: Long = STATIC_OBSERVATION_MS,
    private val staticMaxDrift: Float = STATIC_MAX_DRIFT,
    private val trackMatchDistance: Float = TRACK_MATCH_DISTANCE,
    private val trackStaleMs: Long = TRACK_STALE_MS,
) {
    private data class FaceTrack(
        val trackKey: Int,
        val anchorX: Float,
        val anchorY: Float,
        var centerX: Float,
        var centerY: Float,
        var firstSeenAt: Long,
        var lastSeenAt: Long,
        var maxDriftFromAnchor: Float,
    )

    private val tracks = mutableMapOf<Int, FaceTrack>()
    private var nextSyntheticTrackKey = 1

    fun filter(
        faces: List<FaceFilterInput>,
        now: Long,
    ): List<FaceDebugBox> {
        pruneStaleTracks(now)
        val matchedTrackKeys = mutableSetOf<Int>()

        return faces.map { face ->
            val tooSmall = isTooSmall(face.box)
            if (tooSmall) {
                FaceDebugBox(
                    box = face.box,
                    inRoi = face.inRoi,
                    confidence = face.confidence,
                    accepted = false,
                    rejectReason = REJECT_TOO_SMALL,
                )
            } else {
                val trackKey = resolveTrackKey(face, now, matchedTrackKeys)
                val track = tracks.getValue(trackKey)
                val static = isStaticPoster(track, now)
                FaceDebugBox(
                    box = face.box,
                    inRoi = face.inRoi,
                    confidence = face.confidence,
                    accepted = !static,
                    rejectReason = if (static) REJECT_STATIC else null,
                )
            }
        }
    }

    fun reset() {
        tracks.clear()
        nextSyntheticTrackKey = 1
    }

    private fun isTooSmall(box: NormalizedRect): Boolean {
        val width = box.right - box.left
        val height = box.bottom - box.top
        return width < minWidthFraction || height < minHeightFraction
    }

    private fun resolveTrackKey(
        face: FaceFilterInput,
        now: Long,
        matchedTrackKeys: MutableSet<Int>,
    ): Int {
        val center = boxCenter(face.box)
        val mlKitKey = face.trackingId?.takeIf { it >= 0 }
        if (mlKitKey != null && tracks.containsKey(mlKitKey)) {
            updateTrack(tracks.getValue(mlKitKey), center, now)
            matchedTrackKeys += mlKitKey
            return mlKitKey
        }

        val nearest = tracks.values
            .filter { it.trackKey !in matchedTrackKeys }
            .minByOrNull { hypot(it.centerX - center.first, it.centerY - center.second) }
        if (nearest != null &&
            hypot(nearest.centerX - center.first, nearest.centerY - center.second) <= trackMatchDistance
        ) {
            updateTrack(nearest, center, now)
            matchedTrackKeys += nearest.trackKey
            return nearest.trackKey
        }

        val key = mlKitKey ?: nextSyntheticTrackKey++
        tracks[key] = FaceTrack(
            trackKey = key,
            anchorX = center.first,
            anchorY = center.second,
            centerX = center.first,
            centerY = center.second,
            firstSeenAt = now,
            lastSeenAt = now,
            maxDriftFromAnchor = 0f,
        )
        matchedTrackKeys += key
        return key
    }

    private fun updateTrack(
        track: FaceTrack,
        center: Pair<Float, Float>,
        now: Long,
    ) {
        track.maxDriftFromAnchor = max(
            track.maxDriftFromAnchor,
            hypot(center.first - track.anchorX, center.second - track.anchorY),
        )
        track.centerX = center.first
        track.centerY = center.second
        track.lastSeenAt = now
    }

    private fun isStaticPoster(track: FaceTrack, now: Long): Boolean {
        val observedMs = now - track.firstSeenAt
        return observedMs >= staticObservationMs && track.maxDriftFromAnchor < staticMaxDrift
    }

    private fun pruneStaleTracks(now: Long) {
        val staleKeys = tracks.filterValues { now - it.lastSeenAt > trackStaleMs }.keys
        staleKeys.forEach { tracks.remove(it) }
    }

    private fun boxCenter(box: NormalizedRect): Pair<Float, Float> {
        val cx = (box.left + box.right) / 2f
        val cy = (box.top + box.bottom) / 2f
        return cx to cy
    }

    companion object {
        const val MIN_WIDTH_FRACTION = 0.05f
        const val MIN_HEIGHT_FRACTION = 0.05f
        const val STATIC_OBSERVATION_MS = 5_000L
        const val STATIC_MAX_DRIFT = 0.008f
        const val TRACK_MATCH_DISTANCE = 0.12f
        const val TRACK_STALE_MS = 2_500L

        const val REJECT_TOO_SMALL = "too_small"
        const val REJECT_STATIC = "static"
    }
}
