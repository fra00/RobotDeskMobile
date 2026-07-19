package com.example.mydeskrobot.data.predictivity

interface PredictivityMiningStore {
    suspend fun getLastMinedDayKey(): String?
    suspend fun setLastMinedDayKey(dayKey: String)
}
