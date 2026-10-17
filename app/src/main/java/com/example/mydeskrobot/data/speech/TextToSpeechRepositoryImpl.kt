package com.example.mydeskrobot.data.speech

import com.example.mydeskrobot.domain.mood.TtsProsody
import com.example.mydeskrobot.domain.repository.TextToSpeechRepository

class TextToSpeechRepositoryImpl(
    private val dataSource: AndroidTextToSpeechDataSource,
) : TextToSpeechRepository {

    override suspend fun speak(
        text: String,
        prosody: TtsProsody,
        languageTag: String?,
    ): Result<Unit> = dataSource.speak(text, prosody, languageTag)

    override fun stop() {
        dataSource.stop()
    }
}
