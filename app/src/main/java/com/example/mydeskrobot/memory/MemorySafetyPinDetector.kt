package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryCategory
import java.util.Locale

/**
 * Deterministic safety/health pinning (Level 1 — no LLM, no schema migration).
 */
object MemorySafetyPinDetector {

    const val SAFETY_CONFIDENCE_FLOOR = 0.95f

    enum class SafetyPinLevel {
        NONE,
        SAFETY,
    }

    private val SAFETY_KEYWORDS = listOf(
        "allergi",
        "intolleran",
        "celiach",
        "diabet",
        "epipen",
        "anafilassi",
        "emergenz",
        "118",
        "112",
        "ospedale",
        "ospedali",
        "ricover",
        "farmaco",
        "insulina",
        "epiless",
        "asmatic",
        "contatto emergenza",
    )

    fun classify(value: String, category: MemoryCategory): SafetyPinLevel {
        if (category !in SAFETY_CATEGORIES) return SafetyPinLevel.NONE
        val normalized = value.trim().lowercase(Locale.ITALIAN)
        if (normalized.isBlank()) return SafetyPinLevel.NONE
        return if (SAFETY_KEYWORDS.any { normalized.contains(it) }) {
            SafetyPinLevel.SAFETY
        } else {
            SafetyPinLevel.NONE
        }
    }

    fun isSafetyPinned(value: String, category: MemoryCategory): Boolean =
        classify(value, category) == SafetyPinLevel.SAFETY

    fun applyConfidenceFloor(confidence: Float, value: String, category: MemoryCategory): Float {
        return if (classify(value, category) == SafetyPinLevel.SAFETY) {
            maxOf(confidence.coerceIn(0f, 1f), SAFETY_CONFIDENCE_FLOOR)
        } else {
            confidence.coerceIn(0f, 1f)
        }
    }

    private val SAFETY_CATEGORIES = setOf(
        MemoryCategory.IDENTITY,
        MemoryCategory.FACT,
    )
}
