package com.example.mydeskrobot.data.vision

/**
 * Bridges one-shot vision capture ([CameraXVisionImageCapture]) and continuous
 * desk presence analysis — both share a single [ProcessCameraProvider].
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
