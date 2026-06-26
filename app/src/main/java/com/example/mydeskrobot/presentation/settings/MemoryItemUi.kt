package com.example.mydeskrobot.presentation.settings

import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity

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

fun MemoryDocumentEntity.toUi(): MemoryItemUi = MemoryItemUi(
    id = id,
    category = category.orEmpty(),
    value = value,
)
