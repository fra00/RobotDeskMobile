package com.example.mydeskrobot.data.speech

class SpeechRecognitionException(
    val errorCode: Int,
    message: String,
) : Exception(message)
