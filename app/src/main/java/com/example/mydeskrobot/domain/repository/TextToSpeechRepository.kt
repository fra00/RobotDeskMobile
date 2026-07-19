package com.example.mydeskrobot.domain.repository

import com.example.mydeskrobot.domain.mood.TtsProsody

interface TextToSpeechRepository {
    suspend fun speak(text: String, prosody: TtsProsody = TtsProsody.NEUTRAL): Result<Unit>
    fun stop()
}
