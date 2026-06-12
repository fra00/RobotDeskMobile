package com.example.mydeskrobot.reasoning

fun interface BodyCapabilitiesProvider {
    suspend fun buildContextSection(): String
}
