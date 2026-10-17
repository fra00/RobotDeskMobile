package com.example.mydeskrobot.data.vision

/**
 * Bridges one-shot vision capture ([CameraXVisionImageCapture]) and on-demand
 * desk presence analysis — both share a single [ProcessCameraProvider].
 * After a photo, resumes ImageAnalysis only if a presence session is active.
 */
object VisionCameraLifecycleCoordinator {

    private var resumePresenceAnalysis: (() -> Unit)? = null

    fun setPresenceResumeHandler(handler: (() -> Unit)?) {
        resumePresenceAnalysis = handler
    }

    fun notifyVisionCaptureEnded() {
        resumePresenceAnalysis?.invoke()
    }
}
