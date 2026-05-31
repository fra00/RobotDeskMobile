package com.example.mydeskrobot.data.lists.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.mydeskrobot.domain.list.ListItemType

@Database(
    entities = [ListItemEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ListItemConverters::class)
abstract class ListItemDatabase : RoomDatabase() {
    abstract fun listItemDao(): ListItemDao
}

class ListItemConverters {
    @TypeConverter
    fun toType(raw: String): ListItemType = ListItemType.valueOf(raw)

    @TypeConverter
    fun fromType(value: ListItemType): String = value.name
}
