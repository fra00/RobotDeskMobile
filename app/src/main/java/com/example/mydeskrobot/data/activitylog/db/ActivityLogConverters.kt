package com.example.mydeskrobot.data.activitylog.db

import androidx.room.TypeConverter
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind

class ActivityLogConverters {
    @TypeConverter
    fun toSource(raw: String): ActivitySource = ActivitySource.valueOf(raw)

    @TypeConverter
    fun fromSource(value: ActivitySource): String = value.name

    @TypeConverter
    fun toEpisodeKind(raw: String): EpisodeKind = EpisodeKind.valueOf(raw)

    @TypeConverter
    fun fromEpisodeKind(value: EpisodeKind): String = value.name

    @TypeConverter
    fun toEpisodeConfidence(raw: String): EpisodeConfidence = EpisodeConfidence.valueOf(raw)

    @TypeConverter
    fun fromEpisodeConfidence(value: EpisodeConfidence): String = value.name
}
