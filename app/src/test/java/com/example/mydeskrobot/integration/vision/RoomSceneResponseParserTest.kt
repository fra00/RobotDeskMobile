package com.example.mydeskrobot.integration.vision

import com.example.mydeskrobot.domain.spatial.RoomType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RoomSceneResponseParserTest {

    @Test
    fun `parse extracts landmarks room type and confidence`() {
        val raw = """
            {
              "landmarks": ["scrivania", "monitor", "lampada"],
              "room_type_hint": "study",
              "description": "Piccolo studio con scrivania e PC",
              "confidence": 0.78
            }
        """.trimIndent()

        val result = RoomSceneResponseParser.parse(raw)

        assertNotNull(result)
        assertEquals(listOf("scrivania", "computer", "lampada"), result!!.landmarks)
        assertEquals(RoomType.STUDY, result.roomTypeHint)
        assertEquals("Piccolo studio con scrivania e PC", result.description)
        assertEquals(0.78f, result.confidence, 0.001f)
    }
}
