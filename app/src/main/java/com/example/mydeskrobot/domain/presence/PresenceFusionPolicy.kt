package com.example.mydeskrobot.domain.presence

/**
 * Pure Kotlin fusion of face/pose frame signals into desk occupancy.
 */
data class PresenceFrameSignals(
    val facesInRoi: Int = 0,
    val maxFaceConfidence: Float = 0f,
    val upperBodyPoseInRoi: Boolean = false,
    val poseConfidence: Float = 0f,
    /** Normalized -1..1 from frame center; null if no face in ROI. */
    val primaryFaceOffsetX: Float? = null,
    val primaryFaceOffsetY: Float? = null,
)

class PresenceFusionPolicy(
    private val faceConfidenceThreshold: Float = 0.6f,
    private val poseConfidenceThreshold: Float = 0.5f,
    private val presentStreakRequired: Int = 3,
    private val absentStreakRequired: Int = 3,
) {
    private var presentStreak = 0
    private var absentStreak = 0

    fun fuse(signals: PresenceFrameSignals): DeskOccupancy {
        val now = System.currentTimeMillis()
        val facePresent = signals.facesInRoi > 0 &&
            signals.maxFaceConfidence >= faceConfidenceThreshold
        val posePresent = signals.upperBodyPoseInRoi &&
            signals.poseConfidence >= poseConfidenceThreshold

        return when {
            facePresent || posePresent -> {
                presentStreak++
                absentStreak = 0
                val confidence = maxOf(
                    if (facePresent) signals.maxFaceConfidence else 0f,
                    if (posePresent) signals.poseConfidence else 0f,
                )
                if (presentStreak >= presentStreakRequired) {
                    DeskOccupancy(
                        state = DeskOccupancyState.PRESENT,
                        lastSeenAt = now,
                        confidence = confidence,
                        updatedAt = now,
                    )
                } else {
                    DeskOccupancy(
                        state = DeskOccupancyState.UNCERTAIN,
                        lastSeenAt = null,
                        confidence = confidence,
                        updatedAt = now,
                    )
                }
            }

            signals.facesInRoi > 0 || signals.upperBodyPoseInRoi -> {
                presentStreak = 0
                absentStreak = 0
                DeskOccupancy(
                    state = DeskOccupancyState.UNCERTAIN,
                    lastSeenAt = null,
                    confidence = maxOf(signals.maxFaceConfidence, signals.poseConfidence),
                    updatedAt = now,
                )
            }

            else -> {
                presentStreak = 0
                absentStreak++
                if (absentStreak >= absentStreakRequired) {
                    DeskOccupancy(
                        state = DeskOccupancyState.ABSENT,
                        lastSeenAt = null,
                        confidence = 0f,
                        updatedAt = now,
                    )
                } else {
                    DeskOccupancy(
                        state = DeskOccupancyState.UNCERTAIN,
                        lastSeenAt = null,
                        confidence = 0f,
                        updatedAt = now,
                    )
                }
            }
        }
    }

    fun reset() {
        presentStreak = 0
        absentStreak = 0
    }
}
