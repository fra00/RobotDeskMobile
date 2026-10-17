package com.example.mydeskrobot.domain.predictivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitCanonicalPolicyTest {

    @Test
    fun `meal by clock overrides wrong lunch label at dinner time`() {
        assertEquals(
            "cena",
            HabitCanonicalPolicy.refineMealByTime("pranzo", 21 * 60 + 51),
        )
        assertEquals(
            "pranzo",
            HabitCanonicalPolicy.refineMealByTime("mangiare_qualcosa", 13 * 60),
        )
        assertEquals(
            "colazione",
            HabitCanonicalPolicy.refineMealByTime("pasto", 8 * 60),
        )
    }

    @Test
    fun `non meal activities unchanged by clock`() {
        assertEquals(
            "passeggiata_cane",
            HabitCanonicalPolicy.refineMealByTime("passeggiata_cane", 21 * 60),
        )
    }

    @Test
    fun `local aliases collapse vague eat phrases`() {
        assertEquals("pasto", HabitCanonicalPolicy.applyLocalAliases("mangiare_qualcosa"))
        assertEquals("passeggiata_cane", HabitCanonicalPolicy.applyLocalAliases("passeggiata_con_il_cane"))
    }
}

class HabitSlotMergePolicyTest {

    @Test
    fun `circular distance wraps midnight`() {
        assertEquals(20, HabitSlotMergePolicy.circularMinutesDelta(23 * 60 + 50, 10))
    }

    @Test
    fun `evening meal fragments merge into one slot`() {
        val fragments = listOf(
            HabitSlot(
                slotKey = "mangiare_qualcosa|1230",
                canonicalLabel = "cena",
                displayLabel = "Mangiare qualcosa",
                typicalTimeMinutes = 20 * 60 + 54,
                hitCount = 1,
                confidence = 0.13f,
            ),
            HabitSlot(
                slotKey = "pranzo|1290",
                canonicalLabel = "cena",
                displayLabel = "Pranzo",
                typicalTimeMinutes = 21 * 60 + 51,
                hitCount = 1,
                confidence = 0.13f,
            ),
            HabitSlot(
                slotKey = "cena|1245",
                canonicalLabel = "cena",
                displayLabel = "Cena",
                typicalTimeMinutes = 20 * 60 + 45,
                hitCount = 2,
                confidence = 0.26f,
            ),
            HabitSlot(
                slotKey = "pranzo|1240",
                canonicalLabel = "cena",
                displayLabel = "pranzo",
                typicalTimeMinutes = 20 * 60 + 44,
                hitCount = 1,
                confidence = 0.13f,
            ),
        )
        val clusters = HabitSlotMergePolicy.clusterByTime(fragments)
        assertEquals(1, clusters.size)
        val merged = HabitSlotMergePolicy.mergeCluster(clusters.single())
        assertEquals("cena", merged.canonicalLabel)
        assertEquals(5, merged.hitCount)
        assertTrue(merged.typicalTimeMinutes in (20 * 60)..(22 * 60))
    }

    @Test
    fun `same label far apart stays two clusters`() {
        val slots = listOf(
            HabitSlot(
                slotKey = "passeggiata_cane|480",
                canonicalLabel = "passeggiata_cane",
                displayLabel = "Passeggiata",
                typicalTimeMinutes = 8 * 60,
                hitCount = 3,
            ),
            HabitSlot(
                slotKey = "passeggiata_cane|1140",
                canonicalLabel = "passeggiata_cane",
                displayLabel = "Passeggiata",
                typicalTimeMinutes = 19 * 60,
                hitCount = 2,
            ),
        )
        val clusters = HabitSlotMergePolicy.clusterByTime(slots)
        assertEquals(2, clusters.size)
        assertFalse(
            HabitSlotMergePolicy.areTimeCompatible(8 * 60, 19 * 60),
        )
    }
}
