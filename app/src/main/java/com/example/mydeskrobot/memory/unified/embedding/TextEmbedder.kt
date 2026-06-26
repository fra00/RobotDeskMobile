package com.example.mydeskrobot.memory.unified.embedding

/**
 * On-device sentence embedding for unified memory semantic search.
 * Returns null when the model is unavailable (token-only fallback).
 */
interface TextEmbedder {
    val isAvailable: Boolean
    val embeddingDimension: Int

    suspend fun embed(text: String): FloatArray?

    fun close()
}

object NoOpTextEmbedder : TextEmbedder {
    override val isAvailable: Boolean = false
    override val embeddingDimension: Int = 0

    override suspend fun embed(text: String): FloatArray? = null

    override fun close() = Unit
}
