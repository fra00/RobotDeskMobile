package com.example.mydeskrobot.domain.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialIntentDetectorTest {

    @Test
    fun detect_sceneChanged_invalidates() {
        val result = SpatialIntentDetector.detect("sei in un'altra stanza")
        assertEquals(SpatialIntentDetector.SpatialIntent.SCENE_CHANGED, result.intent)
        assertTrue(result.shouldInvalidateCurrentPlace)
    }

    @Test
    fun detect_localize() {
        val result = SpatialIntentDetector.detect("dove siamo adesso?")
        assertEquals(SpatialIntentDetector.SpatialIntent.LOCALIZE, result.intent)
    }

    @Test
    fun detect_memorizeWithNamedPlace() {
        val result = SpatialIntentDetector.detect("memorizza questa stanza, siamo nello studio")
        assertEquals(SpatialIntentDetector.SpatialIntent.MEMORIZE_PLACE, result.intent)
        assertEquals("studio", result.userNamedPlace)
    }
}
