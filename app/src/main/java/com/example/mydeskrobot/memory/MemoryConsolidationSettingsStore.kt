package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryItemEntity

interface MemoryConsolidationSettingsStore {
    suspend fun getLastConsolidatedContentHash(): String?
    suspend fun setLastConsolidatedContentHash(hash: String)
    suspend fun saveConsolidationBackup(items: List<MemoryItemEntity>)
}
