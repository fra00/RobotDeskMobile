package com.example.mydeskrobot.memory

import com.example.mydeskrobot.memory.db.MemoryCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySafetyPinDetectorTest {

    @Test
    fun classify_detects_allergy_fact() {
        val level = MemorySafetyPinDetector.classify(
            value = "L'utente è allergico alle noci",
            category = MemoryCategory.FACT,
        )
        assertEquals(MemorySafetyPinDetector.SafetyPinLevel.SAFETY, level)
    }

    @Test
    fun classify_ignores_pet_name() {
        val level = MemorySafetyPinDetector.classify(
            value = "Il cane si chiama Brina",
            category = MemoryCategory.FACT,
        )
        assertEquals(MemorySafetyPinDetector.SafetyPinLevel.NONE, level)
    }

    @Test
    fun applyConfidenceFloor_raises_safety_facts() {
        val confidence = MemorySafetyPinDetector.applyConfidenceFloor(
            confidence = 0.6f,
            value = "Allergie al lattosio",
            category = MemoryCategory.IDENTITY,
        )
        assertTrue(confidence >= MemorySafetyPinDetector.SAFETY_CONFIDENCE_FLOOR)
    }

    @Test
    fun applyConfidenceFloor_leaves_normal_facts() {
        val confidence = MemorySafetyPinDetector.applyConfidenceFloor(
            confidence = 0.6f,
            value = "Lavora in smart working il martedì",
            category = MemoryCategory.ROUTINE,
        )
        assertFalse(confidence >= MemorySafetyPinDetector.SAFETY_CONFIDENCE_FLOOR)
        assertEquals(0.6f, confidence, 0.001f)
    }
}
