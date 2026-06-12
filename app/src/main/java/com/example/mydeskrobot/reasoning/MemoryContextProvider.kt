package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile

interface MemoryContextProvider {
    suspend fun buildContextFor(
        userText: String,
        profileOverride: MemoryRetrievalProfile? = null,
    ): String
}
