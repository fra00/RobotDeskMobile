package com.example.mydeskrobot.data.speech

import com.example.mydeskrobot.domain.repository.TextToSpeechRepository

class TextToSpeechRepositoryImpl(
    private val dataSource: AndroidTextToSpeechDataSource,
) : TextToSpeechRepository {

    override suspend fun speak(text: String): Result<Unit> = dataSource.speak(text)

    override fun stop() {
        dataSource.stop()
    }
}
