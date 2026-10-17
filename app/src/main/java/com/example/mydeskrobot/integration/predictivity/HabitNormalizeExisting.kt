package com.example.mydeskrobot.integration.predictivity

/**
 * Existing habit context sent to the label-normalize LLM so new labels can map onto known slots.
 */
data class HabitNormalizeExisting(
    val canonicalLabel: String,
    val displayLabel: String,
    val typicalTimeMinutes: Int,
)
