package com.example.mydeskrobot.domain.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceFaceFilterTest {

    private val filter = PresenceFaceFilter(
        staticObservationMs = 1_000L,
        staticMaxDrift = 0.008f,
    )

    private val largeBox = NormalizedRect(0.35f, 0.25f, 0.55f, 0.55f)

    @Test
    fun rejects_tooSmallFace() {
        val tiny = NormalizedRect(0.48f, 0.48f, 0.51f, 0.51f)
        val result = filter.filter(
            faces = listOf(face(box = tiny)),
            now = 1_000L,
        )

        assertFalse(result.single().accepted)
        assertEquals(PresenceFaceFilter.REJECT_TOO_SMALL, result.single().rejectReason)
    }

    @Test
    fun accepts_largeFaceInRoi() {
        val result = filter.filter(
            faces = listOf(face(box = largeBox)),
            now = 1_000L,
        )

        assertTrue(result.single().accepted)
    }

    @Test
    fun rejects_staticPosterAfterObservationWindow() {
        val t0 = 10_000L
        repeat(6) { frame ->
            val result = filter.filter(
                faces = listOf(face(box = largeBox, trackingId = 7)),
                now = t0 + frame * 200L,
            )
            if (frame < 5) {
                assertTrue(result.single().accepted)
            } else {
                assertFalse(result.single().accepted)
                assertEquals(PresenceFaceFilter.REJECT_STATIC, result.single().rejectReason)
            }
        }
    }

    @Test
    fun accepts_faceWithNaturalJitter() {
        val t0 = 20_000L
        val boxes = listOf(
            NormalizedRect(0.35f, 0.25f, 0.55f, 0.55f),
            NormalizedRect(0.36f, 0.26f, 0.56f, 0.56f),
            NormalizedRect(0.34f, 0.24f, 0.54f, 0.54f),
            NormalizedRect(0.37f, 0.27f, 0.57f, 0.57f),
            NormalizedRect(0.33f, 0.23f, 0.53f, 0.53f),
            NormalizedRect(0.38f, 0.28f, 0.58f, 0.58f),
        )
        boxes.forEachIndexed { index, box ->
            val result = filter.filter(
                faces = listOf(face(box = box, trackingId = 9)),
                now = t0 + index * 250L,
            )
            assertTrue(result.single().accepted)
        }
    }

    private fun face(
        box: NormalizedRect,
        trackingId: Int? = null,
    ) = FaceFilterInput(
        trackingId = trackingId,
        box = box,
        inRoi = true,
        confidence = 0.8f,
    )
}
