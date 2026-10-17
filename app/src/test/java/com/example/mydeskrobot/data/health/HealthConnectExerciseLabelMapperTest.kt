package com.example.mydeskrobot.data.health

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthConnectExerciseLabelMapperTest {

    @Test
    fun `maps common exercise types to Italian labels`() {
        assertEquals("bici", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_BIKING))
        assertEquals("bici", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_BIKING_STATIONARY))
        assertEquals("corsa", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_RUNNING))
        assertEquals("corsa", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_RUNNING_TREADMILL))
        assertEquals("camminata", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_WALKING))
        assertEquals("nuoto", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_SWIMMING_POOL))
        assertEquals("pesi", HealthConnectExerciseLabelMapper.labelForExerciseType(HealthConnectExerciseLabelMapper.TYPE_STRENGTH_TRAINING))
        assertEquals("allenamento", HealthConnectExerciseLabelMapper.labelForExerciseType(999))
    }

    @Test
    fun `formatRawPhrase includes title and duration`() {
        val phrase = HealthConnectExerciseLabelMapper.formatRawPhrase(
            label = "bici",
            startTimeMs = 0L,
            endTimeMs = 45 * 60_000L,
            title = "Mattina",
        )
        assertEquals("Mattina · 45 min", phrase)
    }
}
