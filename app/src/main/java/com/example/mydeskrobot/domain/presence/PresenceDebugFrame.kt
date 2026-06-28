package com.example.mydeskrobot.domain.presence

/**
 * Normalized geometry (0–1) from the last ML Kit presence frame for debug overlay.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val label: String,
    val inRoi: Boolean,
)

data class FaceDebugBox(
    val box: NormalizedRect,
    val inRoi: Boolean,
    val confidence: Float,
    val accepted: Boolean = true,
    val rejectReason: String? = null,
)

data class PresenceDebugFrame(
    val capturedAt: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val roi: NormalizedRect,
    val faces: List<FaceDebugBox>,
    val posePoints: List<NormalizedPoint>,
    val signals: PresenceFrameSignals,
    val occupancyState: DeskOccupancyState,
    val occupancyConfidence: Float,
    val facePresent: Boolean,
    val posePresent: Boolean,
)
