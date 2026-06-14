package com.example.mydeskrobot.domain.spatial

import org.junit.Assert.assertTrue
import org.junit.Test

class SpatialPromptFormatterTest {

    @Test
    fun `format includes DOVE SONO block`() {
        val text = SpatialPromptFormatter.format(
            context = SpatialContextSnapshot(
                currentPlaceLabel = "studio",
                confidence = 0.82f,
                resolution = SpatialResolution.AUTONOMOUS,
                lastLandmarks = listOf("scrivania", "monitor"),
            ),
            knownPlaceLabels = listOf("camera", "studio", "cucina"),
        )

        assertTrue(text.contains("DOVE SONO"))
        assertTrue(text.contains("studio"))
        assertTrue(text.contains("0.82"))
        assertTrue(text.contains("scrivania, monitor"))
        assertTrue(text.contains("camera, studio, cucina"))
    }
}
