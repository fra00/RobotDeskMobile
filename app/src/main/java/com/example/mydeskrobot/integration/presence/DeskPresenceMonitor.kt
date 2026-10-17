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
import com.example.mydeskrobot.data.presence.PresenceDebugStateStore
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * On-device desk presence monitor using ML Kit face + pose detection.
 *
 * Camera is **off by default**. Callers use [withCamera] for short on-demand
 * sessions (look-around gate, idle re-acquire, look_at_user, body locate).
 * Vision tools ([com.example.mydeskrobot.data.vision.CameraXVisionImageCapture])
 * bind the shared camera separately for photos.
 */
class DeskPresenceMonitor(
    private val context: Context,
    private val settingsRepository: DeskPresenceSettingsRepository,
    private val scope: CoroutineScope,
) : DeskPresenceCameraSession {
    private val detector = FacePosePresenceDetector()
    private var fusionPolicy = PresenceFusionPolicy()
    private var faceConfidenceThreshold = 0.6f
    private var analysisJob: Job? = null
    private var watchdogJob: Job? = null
    private val running = AtomicBoolean(false)
    private val binding = AtomicBoolean(false)
    private val sessionDepth = AtomicInteger(0)
    private val sessionMutex = Mutex()
    private val frameMutex = Mutex()
    private var lastFrameProcessedAt = 0L
    private var lastBindAt = 0L
    private var lastResumeAttemptAt = 0L
    private var minFrameIntervalMs = 200L

    private val _occupancy = MutableStateFlow(DeskOccupancy.UNKNOWN)
    val occupancy: StateFlow<DeskOccupancy> = _occupancy.asStateFlow()

    override suspend fun <T> withCamera(block: suspend () -> T): T {
        val settings = settingsRepository.load()
        if (!settings.enabled) {
            return block()
        }
        acquireSession()
        try {
            return block()
        } finally {
            releaseSession()
        }
    }

    override suspend fun awaitOccupancySample(
        timeoutMs: Long,
    ): DeskOccupancy {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(200L)
        while (System.currentTimeMillis() < deadline) {
            val current = DeskPresenceStateStore.current()
            when (current.state) {
                DeskOccupancyState.PRESENT,
                DeskOccupancyState.ABSENT,
                -> return current
                DeskOccupancyState.UNCERTAIN,
                DeskOccupancyState.UNKNOWN,
                -> delay(FRAME_POLL_MS)
            }
        }
        return DeskPresenceStateStore.current()
    }

    private suspend fun acquireSession() {
        sessionMutex.withLock {
            val depth = sessionDepth.incrementAndGet()
            if (depth == 1) {
                startInternal()
                awaitFirstFrame(FIRST_FRAME_TIMEOUT_MS)
            }
        }
    }

    private suspend fun releaseSession() {
        sessionMutex.withLock {
            if (sessionDepth.decrementAndGet() <= 0) {
                sessionDepth.set(0)
                stopInternal(preserveLastFaceSeen = true)
            }
        }
    }

    /**
     * Force camera off and clear session depth (hotword off / settings disable).
     * Clears [FaceGazeStateStore] including lastFaceSeen.
     */
    fun forceStop() {
        scope.launch(Dispatchers.Main) {
            sessionMutex.withLock {
                sessionDepth.set(0)
                stopInternal(preserveLastFaceSeen = false)
            }
        }
    }

    /** Rebind ImageAnalysis after a one-shot vision capture unbinds the shared camera. */
    fun resumeAnalysisIfNeeded() {
        if (!running.get()) return
        val now = System.currentTimeMillis()
        if (now - lastResumeAttemptAt < RESUME_COOLDOWN_MS) return
        lastResumeAttemptAt = now

        scope.launch(Dispatchers.Main) {
            if (!running.get()) return@launch
            val activity = VisionCaptureActivityProvider.getCaptureActivity()
            if (activity == null) {
                Log.w(TAG, "Cannot resume presence analysis — no foreground activity")
                markUncertainStale()
                return@launch
            }
            bindAnalysis(activity)
        }
    }

    private suspend fun startInternal() {
        withContext(Dispatchers.Main) {
            if (!running.compareAndSet(false, true)) return@withContext

            val settings = settingsRepository.load()
            if (!settings.enabled) {
                Log.d(TAG, "Desk presence monitor disabled in settings")
                running.set(false)
                sessionDepth.set(0)
                DeskPresenceCameraCoordinator.publishCameraActive(false)
                return@withContext
            }

            minFrameIntervalMs = (1000L / settings.analysisFps.coerceIn(2, 10))
            fusionPolicy = PresenceFusionPolicy(
                faceConfidenceThreshold = settings.faceConfidenceThreshold,
            )
            faceConfidenceThreshold = settings.faceConfidenceThreshold

            val activity = VisionCaptureActivityProvider.getCaptureActivity()
            if (activity == null) {
                Log.w(TAG, "No foreground activity for camera analysis")
                markUnknownAndStop()
                return@withContext
            }

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Camera permission missing for desk presence")
                markUnknownAndStop()
                return@withContext
            }

            bindAnalysis(activity)
            startWatchdog()
            DeskPresenceCameraCoordinator.publishCameraActive(true)
            Log.i(TAG, "Desk presence on-demand session started")
        }
    }

    private suspend fun stopInternal(preserveLastFaceSeen: Boolean) {
        withContext(Dispatchers.Main) {
            if (!running.compareAndSet(true, false)) {
                DeskPresenceCameraCoordinator.publishCameraActive(false)
                return@withContext
            }
            watchdogJob?.cancel()
            watchdogJob = null
            analysisJob?.cancel()
            analysisJob = null
            fusionPolicy.reset()
            detector.resetFilters()
            lastFrameProcessedAt = 0L
            lastBindAt = 0L
            lastResumeAttemptAt = 0L
            val unknown = DeskOccupancy.UNKNOWN
            _occupancy.value = unknown
            DeskPresenceStateStore.update(unknown)
            if (preserveLastFaceSeen) {
                // Keep lastFaceSeenAtMs for idle re-acquire scheduling; clear live gaze/debug.
                FaceGazeStateStore.update(null)
            } else {
                FaceGazeStateStore.reset()
            }
            PresenceDebugStateStore.reset()
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            DeskPresenceCameraCoordinator.publishCameraActive(false)
            Log.d(TAG, "Desk presence monitor stopped (preserveLastFace=$preserveLastFaceSeen)")
        }
    }

    private suspend fun awaitFirstFrame(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (!running.get()) return
            if (lastFrameProcessedAt > 0L) return
            delay(FRAME_POLL_MS)
        }
        Log.w(TAG, "Timed out waiting for first presence frame")
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && running.get()) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!running.get()) break
                val now = System.currentTimeMillis()
                if (lastBindAt == 0L) continue
                val lastSignal = maxOf(lastFrameProcessedAt, lastBindAt)
                if (lastFrameProcessedAt == 0L && now - lastBindAt < FRAME_STALE_MS) continue
                if (now - lastSignal < FRAME_STALE_MS) continue

                Log.w(TAG, "Presence frames stale for ${now - lastSignal}ms — rebinding camera")
                markUncertainStale()
                resumeAnalysisIfNeeded()
            }
        }
    }

    private fun markUncertainStale() {
        val now = System.currentTimeMillis()
        val uncertain = DeskOccupancy(
            state = DeskOccupancyState.UNCERTAIN,
            confidence = 0f,
            updatedAt = now,
        )
        _occupancy.value = uncertain
        DeskPresenceStateStore.update(uncertain)
        // Preserve lastFaceSeen across transient frame stalls.
        FaceGazeStateStore.update(null)
        PresenceDebugStateStore.reset()
        fusionPolicy.reset()
        detector.resetFilters()
    }

    private fun markUnknownAndStop() {
        val unknown = DeskOccupancy.UNKNOWN
        _occupancy.value = unknown
        DeskPresenceStateStore.update(unknown)
        running.set(false)
        sessionDepth.set(0)
        DeskPresenceCameraCoordinator.publishCameraActive(false)
    }

    private fun bindAnalysis(activity: ComponentActivity) {
        if (!running.get()) return
        if (!binding.compareAndSet(false, true)) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        cameraProviderFuture.addListener({
            try {
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
                    lastBindAt = System.currentTimeMillis()
                    Log.i(TAG, "Desk presence camera analysis bound")
                }.onFailure { error ->
                    Log.e(TAG, "Failed to bind presence analysis", error)
                    markUnknownAndStop()
                }
            } finally {
                binding.set(false)
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
                val result = detector.analyze(
                    image = inputImage,
                    imageWidth = imageProxy.width,
                    imageHeight = imageProxy.height,
                    faceConfidenceThreshold = faceConfidenceThreshold,
                    capturedAt = now,
                )
                val fused = fusionPolicy.fuse(result.signals)
                _occupancy.value = fused
                DeskPresenceStateStore.update(fused)
                PresenceDebugStateStore.update(
                    result.debug.copy(
                        occupancyState = fused.state,
                        occupancyConfidence = fused.confidence,
                    ),
                )
                updateFaceGaze(result.signals, now)
            }.onFailure { error ->
                Log.w(TAG, "Frame analysis failed: ${error.message}")
            }

            imageProxy.close()
        }
    }

    private fun updateFaceGaze(signals: PresenceFrameSignals, now: Long) {
        val offsetX = signals.primaryFaceOffsetX
        val offsetY = signals.primaryFaceOffsetY
        if (offsetX == null || offsetY == null || signals.facesInRoi == 0) {
            FaceGazeStateStore.update(null)
            return
        }
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
        private const val FRAME_STALE_MS = 4_000L
        private const val WATCHDOG_INTERVAL_MS = 1_500L
        private const val RESUME_COOLDOWN_MS = 2_000L
        private const val FRAME_POLL_MS = 80L
        private const val FIRST_FRAME_TIMEOUT_MS = 2_500L
    }
}
