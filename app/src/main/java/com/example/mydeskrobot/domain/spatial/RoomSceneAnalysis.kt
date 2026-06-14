package com.example.mydeskrobot.domain.spatial

data class RoomSceneAnalysis(
    val landmarks: List<String>,
    val roomTypeHint: RoomType,
    val description: String,
    val confidence: Float,
)
