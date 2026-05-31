package com.example.mydeskrobot.data.lists.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.mydeskrobot.domain.list.ListItemType

@Entity(tableName = "list_items")
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: ListItemType,
    val text: String,
    /** For TODO/SHOPPING: done/bought. For NOTE: typically false. */
    val checked: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
