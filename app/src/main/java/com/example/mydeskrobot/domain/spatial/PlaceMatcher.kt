package com.example.mydeskrobot.domain.spatial

/**
 * Weighted landmark overlap for desk-robot room identification.
 */
object PlaceMatcher {

    private val DISTINCTIVE_WEIGHTS = mapOf(
        "letto" to 1.4f,
        "armadio" to 1.2f,
        "comodino" to 1.1f,
        "scrivania" to 1.2f,
        "computer" to 1.1f,
        "monitor" to 1.0f,
        "televisore" to 1.0f,
        "attrezzi" to 1.3f,
        "fornello" to 1.4f,
        "frigorifero" to 1.3f,
        "lavandino" to 1.2f,
        "divano" to 1.1f,
    )

    private val ROOM_TYPE_HINTS = mapOf(
        RoomType.BEDROOM to setOf("letto", "armadio", "comodino"),
        RoomType.STUDY to setOf("scrivania", "computer", "monitor", "attrezzi", "televisore"),
        RoomType.KITCHEN to setOf("fornello", "frigorifero", "lavandino"),
        RoomType.LIVING_ROOM to setOf("divano", "televisore"),
        RoomType.BATHROOM to setOf("lavandino"),
    )

    fun match(
        observedLandmarks: Iterable<String>,
        knownPlaces: List<SpatialPlace>,
    ): PlaceMatchResult {
        val observed = RoomLandmarks.normalizeAll(observedLandmarks).toSet()
        if (observed.isEmpty() || knownPlaces.isEmpty()) {
            return PlaceMatchResult(
                bestMatch = null,
                confidence = 0f,
                band = MatchConfidenceBand.NONE,
                candidates = emptyList(),
                inferredRoomType = inferRoomType(observed),
            )
        }

        val candidates = knownPlaces
            .map { place ->
                val stored = RoomLandmarks.normalizeAll(place.landmarks).toSet()
                val score = weightedJaccard(observed, stored)
                PlaceCandidate(
                    placeId = place.id,
                    label = place.label,
                    score = score,
                    roomType = place.roomType,
                )
            }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }

        val best = candidates.firstOrNull()
        val confidence = best?.score ?: 0f

        return PlaceMatchResult(
            bestMatch = best,
            confidence = confidence,
            band = PlaceMatchResult.bandFor(confidence),
            candidates = candidates.take(5),
            inferredRoomType = best?.roomType?.takeIf { it != RoomType.UNKNOWN }
                ?: inferRoomType(observed),
        )
    }

    fun inferRoomType(landmarks: Set<String>): RoomType {
        if (landmarks.isEmpty()) return RoomType.UNKNOWN
        var bestType = RoomType.UNKNOWN
        var bestScore = 0
        for ((type, hints) in ROOM_TYPE_HINTS) {
            val score = landmarks.count { it in hints }
            if (score > bestScore) {
                bestScore = score
                bestType = type
            }
        }
        return if (bestScore >= 2) bestType else RoomType.UNKNOWN
    }

    private fun weightedJaccard(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val intersection = a.intersect(b)
        if (intersection.isEmpty()) return 0f

        val union = a.union(b)
        var interWeight = 0f
        var unionWeight = 0f
        for (item in union) {
            val w = DISTINCTIVE_WEIGHTS[item] ?: 1f
            unionWeight += w
            if (item in intersection) interWeight += w
        }
        return if (unionWeight <= 0f) 0f else (interWeight / unionWeight).coerceIn(0f, 1f)
    }
}
