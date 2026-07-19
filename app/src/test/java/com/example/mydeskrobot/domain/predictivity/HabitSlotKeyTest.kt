package com.example.mydeskrobot.domain.predictivity

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HabitSlotKeyTest {

    @Test
    fun `timeBucketMinutes rounds down to 30 minute bucket`() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        val calendar = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JUNE, 11, 8, 32, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals(510, HabitSlotKey.timeBucketMinutes(calendar.timeInMillis, bucketMinutes = 30, timeZone = tz))
    }

    @Test
    fun `buildSlotKey combines canonical and bucket`() {
        assertEquals("passeggiata_cane|510", HabitSlotKey.buildSlotKey("passeggiata_cane", 510))
    }

    @Test
    fun `parseSlotKey round trips`() {
        val parsed = HabitSlotKey.parseSlotKey("passeggiata_cane|510")
        assertEquals("passeggiata_cane" to 510, parsed)
    }

    @Test
    fun `timeBucketMinutes groups nearby times into same 30 minute bucket`() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        listOf(35, 40, 45).forEach { minute ->
            val calendar = Calendar.getInstance(tz).apply {
                set(2026, Calendar.JUNE, 11, 8, minute, 0)
                set(Calendar.MILLISECOND, 0)
            }
            assertEquals(
                510,
                HabitSlotKey.timeBucketMinutes(calendar.timeInMillis, bucketMinutes = 30, timeZone = tz),
            )
        }
    }

    @Test
    fun `timeBucketMinutes separates morning and evening slots`() {
        val tz = TimeZone.getTimeZone("Europe/Rome")
        val morning = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JUNE, 11, 8, 45, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val evening = Calendar.getInstance(tz).apply {
            set(2026, Calendar.JUNE, 11, 18, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val morningBucket = HabitSlotKey.timeBucketMinutes(morning.timeInMillis, timeZone = tz)
        val eveningBucket = HabitSlotKey.timeBucketMinutes(evening.timeInMillis, timeZone = tz)
        assertEquals(510, morningBucket)
        assertEquals(1080, eveningBucket)
    }
}
