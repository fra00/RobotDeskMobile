package com.example.mydeskrobot.reasoning

interface ActivityContextProvider {
    suspend fun buildPromptSection(): String
    suspend fun buildHeartbeatSection(): String
}
