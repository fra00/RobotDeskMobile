package com.example.mydeskrobot.data.activitylog.db

import androidx.room.TypeConverter
import com.example.mydeskrobot.domain.activitylog.ActivitySource

class ActivityLogConverters {
    @TypeConverter
    fun toSource(raw: String): ActivitySource = ActivitySource.valueOf(raw)

    @TypeConverter
    fun fromSource(value: ActivitySource): String = value.name
}
