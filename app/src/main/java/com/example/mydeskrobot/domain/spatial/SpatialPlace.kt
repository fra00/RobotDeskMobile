package com.example.mydeskrobot.domain.spatial

data class SpatialPlace(
    val id: Long,
    val label: String,
    val roomType: RoomType,
    val landmarks: List<String>,
    val description: String,
    val aliases: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSeenAt: Long,
)
