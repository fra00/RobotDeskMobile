package com.example.mydeskrobot.domain.repository

import com.example.mydeskrobot.domain.mood.TtsProsody

interface TextToSpeechRepository {
    /**
     * @param languageTag optional BCP-47 override for this utterance (e.g. en-US).
     *        Null keeps the default Italian voice.
     */
    suspend fun speak(
        text: String,
        prosody: TtsProsody = TtsProsody.NEUTRAL,
        languageTag: String? = null,
    ): Result<Unit>

    fun stop()
}
