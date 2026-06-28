package com.example.mydeskrobot.integration.presence

import android.graphics.RectF
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

/**
 * Runs ML Kit face + pose detection on a camera frame and extracts ROI signals.
 */
class FacePosePresenceDetector {

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
    ): PresenceFrameSignals {
        val roi = deskRoi(imageWidth, imageHeight)
        val faces = detectFaces(image)
        val pose = detectPose(image)

        val facesInRoi = faces.count { faceInRoi(it, roi) }
        val facesInRoiList = faces.filter { faceInRoi(it, roi) }
        val maxFaceConfidence = facesInRoiList
            .maxOfOrNull { estimateFaceConfidence(it) }
            ?: 0f

        val primaryFace = facesInRoiList.maxByOrNull { estimateFaceConfidence(it) }
        val (offsetX, offsetY) = primaryFace?.boundingBox?.let { box ->
            val cx = box.exactCenterX() / imageWidth
            val cy = box.exactCenterY() / imageHeight
            ((cx - 0.5f) * 2f) to ((cy - 0.5f) * 2f)
        } ?: (null to null)

        val poseSignals = poseSignals(pose, roi, imageWidth, imageHeight)

        return PresenceFrameSignals(
            facesInRoi = facesInRoi,
            maxFaceConfidence = maxFaceConfidence,
            upperBodyPoseInRoi = poseSignals.first,
            poseConfidence = poseSignals.second,
            primaryFaceOffsetX = offsetX,
            primaryFaceOffsetY = offsetY,
        )
    }

    fun close() {
        faceDetector.close()
        poseDetector.close()
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

    private fun deskRoi(width: Int, height: Int): RectF {
        val marginX = width * 0.2f
        val marginY = height * 0.2f
        return RectF(marginX, marginY, width - marginX, height - marginY)
    }

    private fun faceInRoi(face: Face, roi: RectF): Boolean {
        val box = face.boundingBox ?: return false
        val centerX = box.exactCenterX()
        val centerY = box.exactCenterY()
        return roi.contains(centerX, centerY)
    }

    private fun estimateFaceConfidence(face: Face): Float {
        val box = face.boundingBox ?: return 0.5f
        val area = box.width().toFloat() * box.height().toFloat()
        return (0.5f + (area / 50_000f)).coerceIn(0.4f, 1f)
    }

    private fun poseSignals(
        pose: Pose?,
        roi: RectF,
        imageWidth: Int,
        imageHeight: Int,
    ): Pair<Boolean, Float> {
        if (pose == null) return false to 0f

        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        val landmarks = listOfNotNull(nose, leftShoulder, rightShoulder)
        if (landmarks.isEmpty()) return false to 0f

        val inRoiCount = landmarks.count { landmark ->
            roi.contains(landmark.position.x, landmark.position.y)
        }
        if (inRoiCount == 0) return false to 0f

        val shoulderSpan = if (leftShoulder != null && rightShoulder != null) {
            kotlin.math.abs(leftShoulder.position.x - rightShoulder.position.x)
        } else {
            imageWidth * 0.15f
        }
        val confidence = (inRoiCount / 3f) *
            (shoulderSpan / (imageWidth * 0.2f)).coerceIn(0.4f, 1f)

        return (inRoiCount >= 2) to confidence.coerceIn(0f, 1f)
    }
}
