package com.example.mydeskrobot.domain.vision

interface VisionImageCapture {
    suspend fun captureJpeg(): Result<CapturedImage>
}
