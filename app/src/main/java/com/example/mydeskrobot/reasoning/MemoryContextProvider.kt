package com.example.mydeskrobot.reasoning

import com.example.mydeskrobot.reasoning.memory.MemoryRecallPlan
import com.example.mydeskrobot.reasoning.memory.MemoryRetrievalProfile

interface MemoryContextProvider {
    suspend fun buildContextFor(
        userText: String,
        recallPlan: MemoryRecallPlan?,
        profileOverride: MemoryRetrievalProfile? = null,
        options: MemoryContextOptions = MemoryContextOptions(),
    ): String
}
