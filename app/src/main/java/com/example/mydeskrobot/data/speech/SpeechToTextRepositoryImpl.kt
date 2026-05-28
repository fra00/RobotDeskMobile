package com.example.mydeskrobot.data.speech

import com.example.mydeskrobot.domain.repository.SpeechToTextRepository
import com.example.mydeskrobot.domain.speech.WakePhraseMatcher

class SpeechToTextRepositoryImpl(
    private val dataSource: AndroidSpeechToTextDataSource,
    private val wakePhraseMatcher: WakePhraseMatcher,
) : SpeechToTextRepository {

    override fun isAvailable(): Boolean = dataSource.isRecognitionAvailable()

    override suspend fun listenForQuestion(): Result<String> {
        return dataSource.listenOnce().map { transcript ->
            wakePhraseMatcher.extractQueryOrFull(transcript)
        }.mapCatching { query ->
            require(query.isNotBlank()) { "Empty question after speech recognition" }
            query
        }
    }
}
