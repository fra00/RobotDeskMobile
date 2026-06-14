package com.example.mydeskrobot.domain.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMatcherTest {

    private val bedroom = SpatialPlace(
        id = 1L,
        label = "camera",
        roomType = RoomType.BEDROOM,
        landmarks = listOf("letto", "armadio", "comodino"),
        description = "Camera da letto",
        aliases = emptyList(),
        createdAt = 0L,
        updatedAt = 0L,
        lastSeenAt = 0L,
    )

    private val study = SpatialPlace(
        id = 2L,
        label = "studio",
        roomType = RoomType.STUDY,
        landmarks = listOf("scrivania", "computer", "monitor", "attrezzi"),
        description = "Studio",
        aliases = listOf("ufficio"),
        createdAt = 0L,
        updatedAt = 0L,
        lastSeenAt = 0L,
    )

    @Test
    fun match_bedroomLandmarks_highConfidence() {
        val result = PlaceMatcher.match(
            observedLandmarks = listOf("letto", "armadio", "comodino"),
            knownPlaces = listOf(bedroom, study),
        )
        assertEquals(1L, result.bestMatch?.placeId)
        assertEquals(MatchConfidenceBand.HIGH, result.band)
        assertEquals(RoomType.BEDROOM, result.inferredRoomType)
    }

    @Test
    fun match_studyWithSynonyms() {
        val result = PlaceMatcher.match(
            observedLandmarks = listOf("scrivania", "pc", "attrezzi"),
            knownPlaces = listOf(bedroom, study),
        )
        assertEquals(2L, result.bestMatch?.placeId)
        assertTrue(result.confidence >= PlaceMatchResult.MEDIUM_THRESHOLD)
    }

    @Test
    fun inferRoomType_fromLandmarksOnly() {
        assertEquals(RoomType.BEDROOM, PlaceMatcher.inferRoomType(setOf("letto", "armadio")))
        assertEquals(RoomType.STUDY, PlaceMatcher.inferRoomType(setOf("scrivania", "computer", "attrezzi")))
    }

    @Test
    fun normalize_synonyms() {
        assertEquals("computer", RoomLandmarks.normalize("PC"))
        assertEquals("comodino", RoomLandmarks.normalize("nightstand"))
    }
}
