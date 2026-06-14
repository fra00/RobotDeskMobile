package com.example.mydeskrobot.data.spatial.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mydeskrobot.domain.spatial.RoomType

@Database(
    entities = [SpatialPlaceEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(SpatialConverters::class)
abstract class SpatialPlaceDatabase : RoomDatabase() {
    abstract fun spatialPlaceDao(): SpatialPlaceDao
}

class SpatialConverters {
    @TypeConverter
    fun toRoomType(raw: String): RoomType = RoomType.fromRaw(raw)

    @TypeConverter
    fun fromRoomType(value: RoomType): String = value.name
}
