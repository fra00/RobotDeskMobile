package com.example.mydeskrobot.memory

sealed class AutonomyUpsertResult {
    data class Success(val memoryId: Long) : AutonomyUpsertResult()
    data object IntentCapReached : AutonomyUpsertResult()
    data object InvalidValue : AutonomyUpsertResult()
}
