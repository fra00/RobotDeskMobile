package com.example.mydeskrobot.domain.time

import java.util.Calendar

object NightModeHelper {

    fun isNightMode(
        config: NightModeConfig,
        calendar: Calendar = Calendar.getInstance(),
    ): Boolean {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val start = config.startHour.coerceIn(0, 23)
        val end = config.endHour.coerceIn(0, 23)

        return if (start == end) {
            false
        } else if (start < end) {
            hour in start until end
        } else {
            hour >= start || hour < end
        }
    }
}
