package com.example.mydeskrobot.reasoning.memory

fun interface MemoryRecallPlanner {
    suspend fun plan(
        userText: String,
        nowMillis: Long,
    ): Result<MemoryRecallPlan>
}
