package com.example.mydeskrobot.data.spatial.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mydeskrobot.domain.spatial.RoomType

@Entity(
    tableName = "spatial_places",
    indices = [
        Index(value = ["label"]),
        Index(value = ["isDeleted"]),
        Index(value = ["updatedAt"]),
    ],
)
data class SpatialPlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val label: String,
    val roomType: RoomType,
    val landmarksJson: String,
    val description: String,
    val aliasesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSeenAt: Long,
    val isDeleted: Boolean = false,
)
