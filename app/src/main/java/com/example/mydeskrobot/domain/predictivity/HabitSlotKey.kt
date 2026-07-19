package com.example.mydeskrobot.domain.predictivity

import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object HabitSlotKey {
    fun timeBucketMinutes(
        timestampMs: Long,
        bucketMinutes: Int = ProactivityConstants.TIME_BUCKET_MINUTES,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMs }
        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return (minutes / bucketMinutes) * bucketMinutes
    }

    fun minutesSinceMidnight(
        timestampMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Int {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timestampMs }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    fun buildSlotKey(canonicalLabel: String, timeBucketMinutes: Int): String =
        "${canonicalLabel.trim()}|$timeBucketMinutes"

    fun parseSlotKey(slotKey: String): Pair<String, Int>? {
        val parts = slotKey.split("|", limit = 2)
        if (parts.size != 2) return null
        val bucket = parts[1].toIntOrNull() ?: return null
        val canonical = parts[0].trim()
        if (canonical.isBlank()) return null
        return canonical to bucket
    }
}
