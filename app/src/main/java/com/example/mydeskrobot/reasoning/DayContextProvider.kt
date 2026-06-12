package com.example.mydeskrobot.reasoning

fun interface DayContextProvider {
    suspend fun buildContextSection(userText: String): String
}
