package com.example.mydeskrobot.reasoning

fun interface MemoryContextProvider {
    suspend fun buildContextFor(userText: String): String
}
