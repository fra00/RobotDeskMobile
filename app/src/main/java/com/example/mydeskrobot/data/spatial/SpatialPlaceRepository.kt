package com.example.mydeskrobot.data.spatial

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.spatial.db.SpatialPlaceDao
import com.example.mydeskrobot.data.spatial.db.SpatialPlaceDatabase
import com.example.mydeskrobot.data.spatial.db.SpatialPlaceEntity
import com.example.mydeskrobot.domain.spatial.PlaceMatchResult
import com.example.mydeskrobot.domain.spatial.PlaceMatcher
import com.example.mydeskrobot.domain.spatial.RoomLandmarks
import com.example.mydeskrobot.domain.spatial.RoomType
import com.example.mydeskrobot.domain.spatial.SpatialPlace
import com.example.mydeskrobot.domain.spatial.SpatialJsonArrays

class SpatialPlaceRepository(
    private val dao: SpatialPlaceDao,
) {

    suspend fun listActive(): List<SpatialPlace> =
        dao.getAllActive().map(::toDomain)

    suspend fun getById(id: Long): SpatialPlace? =
        dao.getById(id)?.let(::toDomain)

    suspend fun matchLandmarks(landmarks: Iterable<String>): PlaceMatchResult =
        PlaceMatcher.match(landmarks, listActive())

    suspend fun savePlace(
        label: String,
        landmarks: Iterable<String>,
        description: String,
        roomType: RoomType,
        aliases: Iterable<String> = emptyList(),
        placeId: Long? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val normalizedLabel = label.trim()
        require(normalizedLabel.isNotBlank()) { "label required" }

        val normalizedLandmarks = RoomLandmarks.normalizeAll(landmarks)
        val normalizedAliases = aliases.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val resolvedType = if (roomType == RoomType.UNKNOWN) {
            PlaceMatcher.inferRoomType(normalizedLandmarks.toSet())
        } else {
            roomType
        }

        val existing = placeId?.let { dao.getById(it) }
            ?: dao.findByLabel(normalizedLabel)

        return if (existing != null) {
            val mergedLandmarks = RoomLandmarks.merge(
                decodeJsonArray(existing.landmarksJson),
                normalizedLandmarks,
            )
            val mergedAliases = (decodeJsonArray(existing.aliasesJson) + normalizedAliases).distinct()
            val updated = existing.copy(
                label = normalizedLabel,
                roomType = resolvedType,
                landmarksJson = encodeJsonArray(mergedLandmarks),
                description = description.trim().ifBlank { existing.description },
                aliasesJson = encodeJsonArray(mergedAliases),
                updatedAt = now,
                lastSeenAt = now,
                isDeleted = false,
            )
            dao.update(updated)
            updated.id
        } else {
            dao.insert(
                SpatialPlaceEntity(
                    label = normalizedLabel,
                    roomType = resolvedType,
                    landmarksJson = encodeJsonArray(normalizedLandmarks),
                    description = description.trim(),
                    aliasesJson = encodeJsonArray(normalizedAliases),
                    createdAt = now,
                    updatedAt = now,
                    lastSeenAt = now,
                ),
            )
        }
    }

    suspend fun updateLabelAndLandmarks(
        id: Long,
        label: String,
        landmarks: Iterable<String>,
        description: String?,
    ): Boolean {
        val existing = dao.getById(id) ?: return false
        val now = System.currentTimeMillis()
        val normalizedLandmarks = RoomLandmarks.normalizeAll(landmarks)
        dao.update(
            existing.copy(
                label = label.trim().ifBlank { existing.label },
                landmarksJson = encodeJsonArray(normalizedLandmarks),
                description = description?.trim()?.ifBlank { existing.description } ?: existing.description,
                roomType = PlaceMatcher.inferRoomType(normalizedLandmarks.toSet())
                    .takeIf { it != RoomType.UNKNOWN } ?: existing.roomType,
                updatedAt = now,
            ),
        )
        return true
    }

    suspend fun touchLastSeen(id: Long) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(lastSeenAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
    }

    suspend fun softDelete(id: Long) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun labelSummaries(): List<String> =
        listActive().map { it.label }

    private fun toDomain(entity: SpatialPlaceEntity): SpatialPlace =
        SpatialPlace(
            id = entity.id,
            label = entity.label,
            roomType = entity.roomType,
            landmarks = decodeJsonArray(entity.landmarksJson),
            description = entity.description,
            aliases = decodeJsonArray(entity.aliasesJson),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastSeenAt = entity.lastSeenAt,
        )

    companion object {
        fun create(context: Context): SpatialPlaceRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                SpatialPlaceDatabase::class.java,
                "spatial_places.db",
            ).build()
            return SpatialPlaceRepository(db.spatialPlaceDao())
        }

        fun createForTest(dao: SpatialPlaceDao): SpatialPlaceRepository =
            SpatialPlaceRepository(dao)

        fun encodeJsonArray(items: Iterable<String>): String = SpatialJsonArrays.encode(items)

        fun decodeJsonArray(json: String): List<String> = SpatialJsonArrays.decode(json)
    }
}
