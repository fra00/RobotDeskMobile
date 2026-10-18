package com.example.mydeskrobot.domain.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessSnapshotParserTest {

    @Test
    fun `fromFullLlmJson parses domains and notify flag`() {
        val json = """
            {
              "reply": "",
              "speak_confidence": 0.0,
              "emotion": "neutral",
              "action": {"type": "none"},
              "wellness": {
                "day_key": "2026-10-18",
                "notify_suggested": true,
                "domains": [
                  {"id": "pasti", "status": "attention", "summary_it": "Pranzo non registrato"},
                  {"id": "attivita_fisica", "status": "ok", "summary_it": "Passeggiata ok"}
                ]
              }
            }
        """.trimIndent()

        val snapshot = WellnessSnapshotParser.fromFullLlmJson(json, computedAtMs = 1_700_000_000_000L)
        assertNotNull(snapshot)
        requireNotNull(snapshot)
        assertEquals("2026-10-18", snapshot.dayKey)
        assertTrue(snapshot.notifySuggested)
        assertTrue(snapshot.needsAttention)
        assertEquals(2, snapshot.domains.size)
        assertEquals(WellnessDomainStatus.ATTENTION, snapshot.domains[0].status)
        assertEquals("Pranzo non registrato", snapshot.domains[0].summaryIt)
        assertEquals("pasti", snapshot.topAttentionEntry()?.id)
    }

    @Test
    fun `fromFullLlmJson returns null without wellness object`() {
        assertNull(WellnessSnapshotParser.fromFullLlmJson("""{"reply":"ciao"}"""))
    }

    @Test
    fun `fromFullLlmJson skips invalid domain rows`() {
        val json = """
            {
              "wellness": {
                "domains": [
                  {"id": "", "status": "ok", "summary_it": "x"},
                  {"id": "pasti", "status": "weird", "summary_it": "y"},
                  {"id": "pasti", "status": "ok", "summary_it": "ok pasti"}
                ]
              }
            }
        """.trimIndent()
        val snapshot = WellnessSnapshotParser.fromFullLlmJson(json)
        assertNotNull(snapshot)
        assertEquals(1, snapshot!!.domains.size)
        assertFalse(snapshot.needsAttention)
    }

    @Test
    fun `storage round trip preserves fields`() {
        val original = WellnessSnapshot(
            computedAtMs = 42L,
            dayKey = "2026-10-18",
            domains = listOf(
                WellnessDomainEntry("pasti", WellnessDomainStatus.CRITICAL, "Niente pasti"),
                WellnessDomainEntry("carico_lavoro", WellnessDomainStatus.OK, "Carico ok"),
            ),
            visualOrderDone = true,
            notifySuggested = true,
        )
        val restored = WellnessSnapshotParser.fromStorageJson(
            WellnessSnapshotParser.toStorageJson(original),
        )
        assertEquals(original, restored)
    }

    @Test
    fun `toPromptBlock includes authoritative header`() {
        val snapshot = WellnessSnapshot(
            computedAtMs = 1L,
            dayKey = "2026-10-18",
            domains = listOf(
                WellnessDomainEntry("pasti", WellnessDomainStatus.ATTENTION, "Manca pranzo"),
            ),
            notifySuggested = true,
        )
        val block = snapshot.toPromptBlock()
        assertTrue(block.contains("STATO WELLNESS"))
        assertTrue(block.contains("Pasti"))
        assertTrue(block.contains("Manca pranzo"))
        assertTrue(block.contains("Serve attenzione"))
    }
}
