package com.example.mydeskrobot.data.vision

import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.mydeskrobot.domain.vision.CapturedImage
import com.example.mydeskrobot.domain.vision.VisionImageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class CameraXVisionImageCapture : VisionImageCapture {

    override suspend fun captureJpeg(): Result<CapturedImage> {
        return withContext(Dispatchers.Main) {
            val activity = VisionCaptureActivityProvider.getCaptureActivity()
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        "App not in foreground — bring My Desk Robot to the screen",
                    ),
                )

            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext Result.failure(SecurityException("Camera permission not granted"))
            }

            captureOnActivity(activity)
        }
    }

    private suspend fun captureOnActivity(activity: ComponentActivity): Result<CapturedImage> =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val captureStarted = AtomicBoolean(false)
                val mainExecutor = ContextCompat.getMainExecutor(activity)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)

                fun cleanup(
                    provider: ProcessCameraProvider?,
                    surface: Surface?,
                    surfaceTexture: SurfaceTexture?,
                ) {
                    provider?.unbindAll()
                    surface?.release()
                    surfaceTexture?.release()
                    VisionCameraLifecycleCoordinator.notifyVisionCaptureEnded()
                }

                continuation.invokeOnCancellation {
                    runCatching {
                        cameraProviderFuture.get().unbindAll()
                        VisionCameraLifecycleCoordinator.notifyVisionCaptureEnded()
                    }
                }

                cameraProviderFuture.addListener(
                    {
                        if (!continuation.isActive) return@addListener

                        runCatching {
                            val cameraProvider = cameraProviderFuture.get()
                            val imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            val preview = Preview.Builder().build()

                            val cameraSelector = selectCamera(cameraProvider)
                                ?: throw IllegalStateException("No camera available on this device")

                            val surfaceTexture = SurfaceTexture(0).apply {
                                setDefaultBufferSize(PREVIEW_BUFFER_WIDTH, PREVIEW_BUFFER_HEIGHT)
                            }
                            val previewSurface = Surface(surfaceTexture)

                            preview.setSurfaceProvider { request ->
                                request.provideSurface(previewSurface, mainExecutor) {
                                    previewSurface.release()
                                    surfaceTexture.release()
                                }
                            }

                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                activity,
                                cameraSelector,
                                preview,
                                imageCapture,
                            )

                            val photoFile = File(
                                activity.cacheDir,
                                "vision_${System.currentTimeMillis()}.jpg",
                            )
                            val outputOptions =
                                ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!continuation.isActive) {
                                    cleanup(cameraProvider, previewSurface, surfaceTexture)
                                    return@postDelayed
                                }
                                if (!captureStarted.compareAndSet(false, true)) {
                                    return@postDelayed
                                }

                                imageCapture.takePicture(
                                    outputOptions,
                                    mainExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(
                                            outputFileResults: ImageCapture.OutputFileResults,
                                        ) {
                                            cleanup(cameraProvider, null, null)

                                            if (!continuation.isActive) {
                                                photoFile.delete()
                                                return
                                            }

                                            val scaled = runCatching {
                                                val raw = photoFile.readBytes()
                                                photoFile.delete()
                                                val bytes = JpegImageScaler.scaleJpeg(raw)
                                                CapturedImage(jpegBytes = bytes)
                                            }
                                            continuation.resume(scaled)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            cleanup(cameraProvider, previewSurface, surfaceTexture)
                                            photoFile.delete()
                                            if (continuation.isActive) {
                                                continuation.resume(Result.failure(exception))
                                            }
                                        }
                                    },
                                )
                            }, CAMERA_WARMUP_MS)
                        }.onFailure { error ->
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(error))
                            }
                        }
                    },
                    mainExecutor,
                )
            }
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
        private const val PREVIEW_BUFFER_WIDTH = 640
        private const val PREVIEW_BUFFER_HEIGHT = 480
        /** CameraX needs a short delay after bind before takePicture is valid. */
        private const val CAMERA_WARMUP_MS = 600L
    }
}
