package com.example.mydeskrobot.domain.presence

enum class DeskOccupancyState {
    UNKNOWN,
    PRESENT,
    ABSENT,
    UNCERTAIN,
}

data class DeskOccupancy(
    val state: DeskOccupancyState,
    val lastSeenAt: Long? = null,
    val confidence: Float = 0f,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        val UNKNOWN = DeskOccupancy(state = DeskOccupancyState.UNKNOWN)
    }
}
