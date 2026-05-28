package com.example.mydeskrobot.domain.repository

interface TextToSpeechRepository {
    suspend fun speak(text: String): Result<Unit>
    fun stop()
}
