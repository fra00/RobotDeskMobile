package com.example.mydeskrobot.memory

interface MemoryReorganizeSettingsStore {
    suspend fun loadReorganizeConfig(): MemoryReorganizeConfig
    suspend fun getLastManualReorganizeAtMs(): Long?
    suspend fun setLastManualReorganizeAtMs(value: Long)
}
