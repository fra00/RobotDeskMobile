package com.example.mydeskrobot.integration.presence

import android.graphics.RectF
import com.example.mydeskrobot.domain.presence.FaceDebugBox
import com.example.mydeskrobot.domain.presence.FaceFilterInput
import com.example.mydeskrobot.domain.presence.NormalizedPoint
import com.example.mydeskrobot.domain.presence.NormalizedRect
import com.example.mydeskrobot.domain.presence.PresenceDebugFrame
import com.example.mydeskrobot.domain.presence.PresenceFaceFilter
import com.example.mydeskrobot.domain.presence.PresenceFrameSignals
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.pose.PoseLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PresenceAnalysisResult(
    val signals: PresenceFrameSignals,
    val debug: PresenceDebugFrame,
)

/**
 * Runs ML Kit face + pose detection on a camera frame and extracts ROI signals.
 */
class FacePosePresenceDetector {

    private val faceFilter = PresenceFaceFilter()

    private val faceDetector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .enableTracking()
            .build(),
    )

    private val poseDetector: PoseDetector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build(),
    )

    suspend fun analyze(
        image: InputImage,
        imageWidth: Int,
        imageHeight: Int,
        faceConfidenceThreshold: Float,
        capturedAt: Long = System.currentTimeMillis(),
    ): PresenceAnalysisResult {
        val roi = deskRoi(imageWidth, imageHeight)
        val faces = detectFaces(image)
        val pose = detectPose(image)

        val faceFilterInputs = faces.map { face ->
            val box = face.boundingBox?.toNormalized(imageWidth, imageHeight)
                ?: NormalizedRect(0f, 0f, 0f, 0f)
            FaceFilterInput(
                trackingId = face.trackingId,
                box = box,
                inRoi = faceInRoi(face, roi),
                confidence = estimateFaceConfidence(face),
            )
        }
        val faceDebugList = faceFilter.filter(faceFilterInputs, capturedAt)

        val acceptedInRoi = faceDebugList.filter { it.accepted && it.inRoi }
        val facesInRoi = acceptedInRoi.size
        val maxFaceConfidence = acceptedInRoi.maxOfOrNull { it.confidence } ?: 0f

        val primaryFace = acceptedInRoi.maxByOrNull { it.confidence }
        val (offsetX, offsetY) = primaryFace?.box?.let { box ->
            val cx = (box.left + box.right) / 2f
            val cy = (box.top + box.bottom) / 2f
            ((cx - 0.5f) * 2f) to ((cy - 0.5f) * 2f)
        } ?: (null to null)

        val posePoints = buildPosePoints(pose, roi, imageWidth, imageHeight)
        val poseSignals = poseSignalsFromPoints(posePoints, imageWidth)

        val signals = PresenceFrameSignals(
            facesInRoi = facesInRoi,
            maxFaceConfidence = maxFaceConfidence,
            upperBodyPoseInRoi = poseSignals.first,
            poseConfidence = poseSignals.second,
            primaryFaceOffsetX = offsetX,
            primaryFaceOffsetY = offsetY,
        )

        val facePresent = facesInRoi > 0 && maxFaceConfidence >= faceConfidenceThreshold
        val posePresent = poseSignals.first &&
            poseSignals.second >= POSE_CONFIDENCE_THRESHOLD

        val debug = PresenceDebugFrame(
            capturedAt = capturedAt,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            roi = roi.toNormalized(imageWidth, imageHeight),
            faces = faceDebugList,
            posePoints = posePoints,
            signals = signals,
            occupancyState = com.example.mydeskrobot.domain.presence.DeskOccupancyState.UNKNOWN,
            occupancyConfidence = 0f,
            facePresent = facePresent,
            posePresent = posePresent,
        )

        return PresenceAnalysisResult(signals = signals, debug = debug)
    }

    fun close() {
        faceFilter.reset()
        faceDetector.close()
        poseDetector.close()
    }

    fun resetFilters() {
        faceFilter.reset()
    }

    private suspend fun detectFaces(image: InputImage): List<Face> =
        suspendCancellableCoroutine { cont ->
            faceDetector.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

    private suspend fun detectPose(image: InputImage): Pose? =
        suspendCancellableCoroutine { cont ->
            poseDetector.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }

    private fun buildPosePoints(
        pose: Pose?,
        roi: RectF,
        imageWidth: Int,
        imageHeight: Int,
    ): List<NormalizedPoint> {
        if (pose == null) return emptyList()

        return listOf(
            PoseLandmark.NOSE to "nose",
            PoseLandmark.LEFT_SHOULDER to "L_shoulder",
            PoseLandmark.RIGHT_SHOULDER to "R_shoulder",
        ).mapNotNull { (type, label) ->
            pose.getPoseLandmark(type)?.let { landmark ->
                val x = landmark.position.x / imageWidth
                val y = landmark.position.y / imageHeight
                NormalizedPoint(
                    x = x,
                    y = y,
                    label = label,
                    inRoi = roi.contains(landmark.position.x, landmark.position.y),
                )
            }
        }
    }

    private fun poseSignalsFromPoints(
        posePoints: List<NormalizedPoint>,
        imageWidth: Int,
    ): Pair<Boolean, Float> {
        if (posePoints.isEmpty()) return false to 0f

        val inRoiCount = posePoints.count { it.inRoi }
        if (inRoiCount == 0) return false to 0f

        val left = posePoints.find { it.label == "L_shoulder" }
        val right = posePoints.find { it.label == "R_shoulder" }
        val shoulderSpan = if (left != null && right != null) {
            kotlin.math.abs(left.x - right.x) * imageWidth
        } else {
            imageWidth * 0.15f
        }
        val confidence = (inRoiCount / 3f) *
            (shoulderSpan / (imageWidth * 0.2f)).coerceIn(0.4f, 1f)

        return (inRoiCount >= 2) to confidence.coerceIn(0f, 1f)
    }

    private fun deskRoi(width: Int, height: Int): RectF {
        val marginX = width * 0.2f
        val marginY = height * 0.2f
        return RectF(marginX, marginY, width - marginX, height - marginY)
    }

    private fun faceInRoi(face: Face, roi: RectF): Boolean {
        val box = face.boundingBox ?: return false
        return roi.contains(box.exactCenterX(), box.exactCenterY())
    }

    private fun estimateFaceConfidence(face: Face): Float {
        val box = face.boundingBox ?: return 0.5f
        val area = box.width().toFloat() * box.height().toFloat()
        return (0.5f + (area / 50_000f)).coerceIn(0.4f, 1f)
    }

    private fun android.graphics.Rect.toNormalized(width: Int, height: Int): NormalizedRect =
        NormalizedRect(
            left = left.toFloat() / width,
            top = top.toFloat() / height,
            right = right.toFloat() / width,
            bottom = bottom.toFloat() / height,
        )

    private fun RectF.toNormalized(width: Int, height: Int): NormalizedRect =
        NormalizedRect(
            left = left / width,
            top = top / height,
            right = right / width,
            bottom = bottom / height,
        )

    companion object {
        const val POSE_CONFIDENCE_THRESHOLD = 0.5f
    }
}
