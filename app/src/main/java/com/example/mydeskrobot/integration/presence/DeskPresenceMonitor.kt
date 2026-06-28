package com.example.mydeskrobot.integration.presence

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.data.presence.DeskPresenceSettingsRepository
import com.example.mydeskrobot.data.presence.DeskPresenceStateStore
import com.example.mydeskrobot.data.presence.FaceGazeStateStore
import com.example.mydeskrobot.data.vision.VisionCaptureActivityProvider
import com.example.mydeskrobot.domain.presence.DeskOccupancy
import com.example.mydeskrobot.domain.presence.DeskOccupancyState
import com.example.mydeskrobot.domain.presence.FaceGazeSnapshot
import com.example.mydeskrobot.domain.presence.PresenceFrameSignals
import com.example.mydeskrobot.domain.presence.PresenceFusionPolicy
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device desk presence monitor using ML Kit face + pose detection.
 * Runs only while the voice session is active; separate from heartbeat scheduling.
 */
class DeskPresenceMonitor(
    private val context: Context,
    private val settingsRepository: DeskPresenceSettingsRepository,
    private val scope: CoroutineScope,
) {
    private val detector = FacePosePresenceDetector()
    private var fusionPolicy = PresenceFusionPolicy()
    private var analysisJob: Job? = null
    private val running = AtomicBoolean(false)
    private val frameMutex = Mutex()
    private var lastFrameProcessedAt = 0L
    private var minFrameIntervalMs = 200L

    private val _occupancy = MutableStateFlow(DeskOccupancy.UNKNOWN)
    val occupancy: StateFlow<DeskOccupancy> = _occupancy.asStateFlow()

    fun start() {
        if (!running.compareAndSet(false, true)) return

        analysisJob = scope.launch(Dispatchers.Main) {
            val settings = settingsRepository.load()
            if (!settings.enabled) {
                Log.d(TAG, "Desk presence monitor disabled in settings")
                running.set(false)
                return@launch
            }

            minFrameIntervalMs = (1000L / settings.analysisFps.coerceIn(2, 10))
            fusionPolicy = PresenceFusionPolicy(
                faceConfidenceThreshold = settings.faceConfidenceThreshold,
            )

            val activity = VisionCaptureActivityProvider.getCaptureActivity()
            if (activity == null) {
                Log.w(TAG, "No foreground activity for camera analysis")
                running.set(false)
                return@launch
            }

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Camera permission missing for desk presence")
                running.set(false)
                return@launch
            }

            bindAnalysis(activity)
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        analysisJob?.cancel()
        analysisJob = null
        fusionPolicy.reset()
        val unknown = DeskOccupancy.UNKNOWN
        _occupancy.value = unknown
        DeskPresenceStateStore.update(unknown)
        FaceGazeStateStore.reset()
        runCatching {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
        Log.d(TAG, "Desk presence monitor stopped")
    }

    private fun bindAnalysis(activity: ComponentActivity) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            if (!running.get()) return@addListener

            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(mainExecutor) { imageProxy ->
                    if (!running.get()) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    scope.launch(Dispatchers.Default) {
                        processFrame(imageProxy)
                    }
                }

                val cameraSelector = selectCamera(cameraProvider)
                    ?: throw IllegalStateException("No camera available")

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(activity, cameraSelector, analysis)
                Log.i(TAG, "Desk presence camera analysis started")
            }.onFailure { error ->
                Log.e(TAG, "Failed to bind presence analysis", error)
                running.set(false)
            }
        }, mainExecutor)
    }

    private suspend fun processFrame(imageProxy: ImageProxy) {
        frameMutex.withLock {
            val now = System.currentTimeMillis()
            if (now - lastFrameProcessedAt < minFrameIntervalMs) {
                imageProxy.close()
                return
            }
            lastFrameProcessedAt = now

            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val rotation = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            runCatching {
                val signals = detector.analyze(
                    image = inputImage,
                    imageWidth = imageProxy.width,
                    imageHeight = imageProxy.height,
                )
                val fused = fusionPolicy.fuse(signals)
                _occupancy.value = fused
                DeskPresenceStateStore.update(fused)
                updateFaceGaze(signals, now)
            }.onFailure { error ->
                Log.w(TAG, "Frame analysis failed: ${error.message}")
            }

            imageProxy.close()
        }
    }

    private fun updateFaceGaze(signals: PresenceFrameSignals, now: Long) {
        val offsetX = signals.primaryFaceOffsetX
        val offsetY = signals.primaryFaceOffsetY
        if (offsetX == null || offsetY == null || signals.facesInRoi == 0) return
        FaceGazeStateStore.update(
            FaceGazeSnapshot(
                horizontalOffset = offsetX,
                verticalOffset = offsetY,
                confidence = signals.maxFaceConfidence,
                capturedAt = now,
            ),
        )
    }

    private fun selectCamera(provider: ProcessCameraProvider): CameraSelector? {
        if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
            return CameraSelector.DEFAULT_FRONT_CAMERA
        }
        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
            return CameraSelector.DEFAULT_BACK_CAMERA
        }
        return null
    }

    companion object {
        private const val TAG = "DeskPresenceMonitor"
    }
}
