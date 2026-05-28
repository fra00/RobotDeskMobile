package com.example.mydeskrobot.domain.repository

import com.example.mydeskrobot.domain.speech.WakePhraseParseResult

interface SpeechToTextRepository {
    /** Used after on-device hotword detection; no wake-phrase prefix required. */
    suspend fun listenForQuestion(): Result<String>

    fun isAvailable(): Boolean
}
