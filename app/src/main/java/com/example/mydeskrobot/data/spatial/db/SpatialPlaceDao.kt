package com.example.mydeskrobot.data.spatial.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SpatialPlaceDao {

    @Query("SELECT * FROM spatial_places WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    suspend fun getAllActive(): List<SpatialPlaceEntity>

    @Query("SELECT * FROM spatial_places WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: Long): SpatialPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SpatialPlaceEntity): Long

    @Update
    suspend fun update(entity: SpatialPlaceEntity)

    @Query("UPDATE spatial_places SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("SELECT * FROM spatial_places WHERE isDeleted = 0 AND LOWER(label) = LOWER(:label) LIMIT 1")
    suspend fun findByLabel(label: String): SpatialPlaceEntity?
}
