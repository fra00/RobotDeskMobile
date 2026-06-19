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
        .adapter(EpisodicExtractionPayload::class.java)

    @Test
    fun `parses episodic events JSON array`() {
        val json = MemoryExtractionService.extractJsonBody(
            """
            ```json
            {"events": [{"kind": "plan", "label": "cinema", "raw_phrase": "domani cinema", "confidence": "tentative", "scheduled_day": "2026-06-03", "scheduled_time": "20:30", "source_channel": "WhatsApp"}]}
            ```
            """.trimIndent(),
        )
        val payload = adapter.fromJson(json)
        assertNotNull(payload)
        assertEquals(1, payload!!.events.size)
        assertEquals("cinema", payload.events[0].label)
        assertEquals("plan", payload.events[0].kind)
        assertEquals("20:30", payload.events[0].scheduled_time)
    }

    @Test
    fun `parses physical_now event`() {
        val json = MemoryExtractionService.extractJsonBody(
            """{"events": [{"kind": "physical_now", "label": "colazione", "raw_phrase": "vado a colazione"}]}""",
        )
        val payload = adapter.fromJson(json)
        assertNotNull(payload)
        assertEquals("colazione", payload!!.events[0].label)
    }
}
