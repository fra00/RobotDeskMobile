package com.example.mydeskrobot.activity.extract

import com.example.mydeskrobot.memory.extract.MemoryExtractionService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ActivityExtractionServiceParseTest {

    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ActivityExtractionPayload::class.java)

    @Test
    fun `parses activities JSON array`() {
        val json = MemoryExtractionService.extractJsonBody(
            """
            ```json
            {"activities": [{"label": "colazione", "raw_phrase": "vado a colazione"}]}
            ```
            """.trimIndent(),
        )
        val payload = adapter.fromJson(json)
        assertNotNull(payload)
        assertEquals(1, payload!!.activities.size)
        assertEquals("colazione", payload.activities[0].label)
        assertEquals("vado a colazione", payload.activities[0].raw_phrase)
    }
}
