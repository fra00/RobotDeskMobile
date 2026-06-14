package com.example.mydeskrobot.presentation.settings

data class SpatialPlaceUi(
    val id: Long,
    val label: String,
    val roomType: String,
    val landmarks: String,
    val description: String,
    val lastSeenAt: Long,
)
