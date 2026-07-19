package com.example.mydeskrobot.domain.predictivity

fun interface HabitPendingMiner {
    suspend fun minePendingDays(): MiningResult
}
