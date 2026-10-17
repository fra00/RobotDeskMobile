package com.example.mydeskrobot.data.health

/**
 * Maps Health Connect [ExerciseSessionRecord] exerciseType ints to short Italian Log Day labels.
 *
 * Constants mirror `androidx.health.connect.client.records.ExerciseSessionRecord` so JVM unit
 * tests do not need the Android Health Connect client at runtime.
 */
object HealthConnectExerciseLabelMapper {

    // Values match androidx.health.connect ExerciseSessionRecord companion constants.
    const val TYPE_OTHER_WORKOUT = 0
    const val TYPE_BADMINTON = 2
    const val TYPE_BASEBALL = 4
    const val TYPE_BASKETBALL = 5
    const val TYPE_BIKING = 8
    const val TYPE_BIKING_STATIONARY = 9
    const val TYPE_BOOT_CAMP = 10
    const val TYPE_BOXING = 11
    const val TYPE_CALISTHENICS = 13
    const val TYPE_CRICKET = 14
    const val TYPE_DANCING = 16
    const val TYPE_ELLIPTICAL = 25
    const val TYPE_EXERCISE_CLASS = 26
    const val TYPE_HIGH_INTENSITY_INTERVAL_TRAINING = 36
    const val TYPE_HIKING = 37
    const val TYPE_PILATES = 48
    const val TYPE_RACQUETBALL = 50
    const val TYPE_ROWING = 53
    const val TYPE_ROWING_MACHINE = 54
    const val TYPE_RUNNING = 56
    const val TYPE_RUNNING_TREADMILL = 57
    const val TYPE_SOCCER = 64
    const val TYPE_SQUASH = 66
    const val TYPE_STRENGTH_TRAINING = 70
    const val TYPE_STRETCHING = 71
    const val TYPE_SWIMMING_OPEN_WATER = 73
    const val TYPE_SWIMMING_POOL = 74
    const val TYPE_TABLE_TENNIS = 75
    const val TYPE_TENNIS = 76
    const val TYPE_VOLLEYBALL = 78
    const val TYPE_WALKING = 79
    const val TYPE_WEIGHTLIFTING = 81
    const val TYPE_YOGA = 83
    const val TYPE_GUIDED_BREATHING = 33

    fun labelForExerciseType(exerciseType: Int): String = when (exerciseType) {
        TYPE_BIKING, TYPE_BIKING_STATIONARY -> "bici"
        TYPE_RUNNING, TYPE_RUNNING_TREADMILL -> "corsa"
        TYPE_WALKING -> "camminata"
        TYPE_HIKING -> "escursione"
        TYPE_SWIMMING_OPEN_WATER, TYPE_SWIMMING_POOL -> "nuoto"
        TYPE_YOGA, TYPE_PILATES, TYPE_STRETCHING, TYPE_GUIDED_BREATHING -> "stretching"
        TYPE_STRENGTH_TRAINING, TYPE_WEIGHTLIFTING, TYPE_CALISTHENICS -> "pesi"
        TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, TYPE_BOOT_CAMP -> "hiit"
        TYPE_ELLIPTICAL -> "ellittica"
        TYPE_ROWING, TYPE_ROWING_MACHINE -> "canottaggio"
        TYPE_DANCING, TYPE_EXERCISE_CLASS -> "allenamento"
        TYPE_SOCCER, TYPE_BASKETBALL, TYPE_TENNIS, TYPE_VOLLEYBALL,
        TYPE_BADMINTON, TYPE_TABLE_TENNIS, TYPE_SQUASH, TYPE_RACQUETBALL,
        -> "sport"
        else -> "allenamento"
    }

    fun formatRawPhrase(
        label: String,
        startTimeMs: Long,
        endTimeMs: Long,
        title: String?,
    ): String {
        val durationMin = ((endTimeMs - startTimeMs).coerceAtLeast(0L) / 60_000L).toInt()
        val durationPart = if (durationMin > 0) "${durationMin} min" else null
        val titlePart = title?.trim()?.takeIf { it.isNotBlank() && !it.equals(label, ignoreCase = true) }
        return listOfNotNull(titlePart, durationPart).joinToString(" · ").ifBlank { label }
    }
}
