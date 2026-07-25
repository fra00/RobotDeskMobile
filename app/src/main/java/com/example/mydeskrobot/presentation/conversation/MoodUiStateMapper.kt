package com.example.mydeskrobot.presentation.conversation

import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.mood.EphemeralExpression
import com.example.mydeskrobot.domain.mood.IdleDistractionKind
import com.example.mydeskrobot.domain.mood.MoodPromptFormatter
import com.example.mydeskrobot.domain.mood.MoodValenceMapper
import com.example.mydeskrobot.domain.mood.RobotMood

object MoodUiStateMapper {

    fun from(
        mood: RobotMood,
        ephemeral: EphemeralExpression?,
        displayEmotion: RobotEmotion,
        displayIntensity: Float,
        idleMinutes: Long,
        idleDistraction: IdleDistractionKind? = null,
        now: Long = System.currentTimeMillis(),
    ): MoodUiState {
        val activeEphemeral = ephemeral?.takeIf { it.isActive(now) }
        return MoodUiState(
            valence = mood.valence,
            baseline = mood.baseline,
            baseEmotion = mood.baseEmotion,
            baseIntensity = mood.intensity,
            displayEmotion = displayEmotion,
            displayIntensity = displayIntensity,
            reason = mood.reason,
            durationMinutes = mood.durationMinutes(now),
            idleMinutes = idleMinutes,
            recentDeltas = mood.recentDeltas.map { delta ->
                MoodDeltaUi(
                    event = delta.event,
                    deltaFormatted = MoodValenceMapper.formatValence(delta.delta),
                )
            },
            ephemeralEmotion = activeEphemeral?.emotion,
            ephemeralIntensity = activeEphemeral?.intensity,
            ephemeralRemainingSeconds = activeEphemeral?.let {
                ((it.expiresAt - now).coerceAtLeast(0L) + 999L) / 1000L
            },
            promptSnapshot = MoodPromptFormatter.format(
                mood = mood,
                ephemeral = activeEphemeral,
                idleDistraction = idleDistraction,
                now = now,
            ),
        )
    }
}
