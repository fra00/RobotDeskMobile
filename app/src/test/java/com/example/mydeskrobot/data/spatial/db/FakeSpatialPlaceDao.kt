package com.example.mydeskrobot.data.spatial.db

import com.example.mydeskrobot.domain.spatial.RoomType

class FakeSpatialPlaceDao(
    initial: List<SpatialPlaceEntity> = emptyList(),
) : SpatialPlaceDao {
    private val items = initial.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

    override suspend fun getAllActive(): List<SpatialPlaceEntity> =
        items.filter { !it.isDeleted }

    override suspend fun getById(id: Long): SpatialPlaceEntity? =
        items.find { it.id == id && !it.isDeleted }

    override suspend fun insert(entity: SpatialPlaceEntity): Long {
        val id = nextId++
        items.add(entity.copy(id = id))
        return id
    }

    override suspend fun update(entity: SpatialPlaceEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        if (index >= 0) items[index] = entity
    }

    override suspend fun softDelete(id: Long, now: Long) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) items[index] = items[index].copy(isDeleted = true, updatedAt = now)
    }

    override suspend fun findByLabel(label: String): SpatialPlaceEntity? =
        items.find { !it.isDeleted && it.label.equals(label, ignoreCase = true) }
}

fun fakeSpatialPlace(
    id: Long = 0L,
    label: String,
    landmarks: List<String>,
    roomType: RoomType = RoomType.UNKNOWN,
): SpatialPlaceEntity {
    val now = System.currentTimeMillis()
    return SpatialPlaceEntity(
        id = id,
        label = label,
        roomType = roomType,
        landmarksJson = com.example.mydeskrobot.data.spatial.SpatialPlaceRepository.encodeJsonArray(landmarks),
        description = "",
        aliasesJson = "[]",
        createdAt = now,
        updatedAt = now,
        lastSeenAt = now,
    )
}
