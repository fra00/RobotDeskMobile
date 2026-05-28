package com.example.mydeskrobot.memory.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_items",
    indices = [
        Index(value = ["category"]),
        Index(value = ["value"]),
        Index(value = ["isDeleted"]),
        Index(value = ["updatedAt"]),
        Index(value = ["lastUsedAt"]),
    ],
)
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: MemoryCategory,
    val value: String,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long = 0L,
    val sourceMessageId: Long,
    val isDeleted: Boolean = false,
)
