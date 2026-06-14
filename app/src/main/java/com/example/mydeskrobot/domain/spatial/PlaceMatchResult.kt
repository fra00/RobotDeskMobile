package com.example.mydeskrobot.domain.spatial

enum class MatchConfidenceBand {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

data class PlaceCandidate(
    val placeId: Long,
    val label: String,
    val score: Float,
    val roomType: RoomType,
)

data class PlaceMatchResult(
    val bestMatch: PlaceCandidate?,
    val confidence: Float,
    val band: MatchConfidenceBand,
    val candidates: List<PlaceCandidate>,
    val inferredRoomType: RoomType,
) {
    companion object {
        const val HIGH_THRESHOLD = 0.55f
        const val MEDIUM_THRESHOLD = 0.35f

        fun bandFor(score: Float): MatchConfidenceBand = when {
            score >= HIGH_THRESHOLD -> MatchConfidenceBand.HIGH
            score >= MEDIUM_THRESHOLD -> MatchConfidenceBand.MEDIUM
            score > 0f -> MatchConfidenceBand.LOW
            else -> MatchConfidenceBand.NONE
        }
    }
}
