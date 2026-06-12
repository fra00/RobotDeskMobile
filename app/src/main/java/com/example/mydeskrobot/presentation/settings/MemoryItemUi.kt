package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity

data class MemoryItemUi(
    val id: Long,
    val category: String,
    val value: String,
)

fun MemoryItemEntity.toUi(): MemoryItemUi = MemoryItemUi(
    id = id,
    category = category.name,
    value = value,
)
