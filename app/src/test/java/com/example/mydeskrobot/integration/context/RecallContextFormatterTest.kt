package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.data.predictivity.HabitSlotCodec
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.MemoryDocumentSource
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallContextFormatterTest {

    private val now = System.currentTimeMillis()

    @Test
    fun formatRecallBlock_putsHabitSlotsUnderExpectedNotFatti() {
        val slot = HabitSlot(
            slotKey = "tapis_roulant|1140",
            canonicalLabel = "tapis_roulant",
            displayLabel = "tapis roulant",
            typicalTimeMinutes = 1140,
            hitCount = 4,
            confidence = 0.75f,
            lastHitDayKey = "2026-08-05",
        )
        val block = RecallContextFormatter.formatRecallBlock(
            documents = listOf(
                userFact("L'utente si chiama Francesco"),
                MemoryDocumentEntity(
                    id = 2L,
                    value = HabitSlotCodec.encode(slot),
                    kind = MemoryDocumentKind.AUTONOMY.name,
                    category = MemoryCategory.PATTERN.name,
                    source = MemoryDocumentSource.EXTRACTOR.name,
                    confidence = 0.75f,
                    createdAt = now,
                    updatedAt = now,
                ),
                MemoryDocumentEntity(
                    id = 3L,
                    value = "OBSERVATION: scrivania un po' piena",
                    kind = MemoryDocumentKind.AUTONOMY.name,
                    category = MemoryCategory.OBSERVATION.name,
                    source = MemoryDocumentSource.TOOL.name,
                    confidence = 0.8f,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
            temporalScope = TemporalScope.WEEK,
        )

        assertTrue(block.contains("FATTI:"))
        assertTrue(block.contains("L'utente si chiama Francesco"))
        assertTrue(block.contains("ABITUDINI ATTESE:"))
        assertTrue(block.contains("tapis roulant"))
        assertTrue(block.contains("19:00"))
        assertFalse(block.contains("HABIT_SLOT:v1:"))
        assertFalse(
            block.substringAfter("FATTI:").contains("tapis roulant"),
        )
        assertTrue(block.contains("NOTE ROBOT:"))
        assertTrue(block.contains("OBSERVATION: scrivania un po' piena"))
    }

    @Test
    fun formatRecallBlock_putsRoutineUnderDeclaredNotFatti() {
        val block = RecallContextFormatter.formatRecallBlock(
            documents = listOf(
                userFact("L'utente si chiama Francesco"),
                MemoryDocumentEntity(
                    id = 4L,
                    value = "Martedì e giovedì fa tapis roulant",
                    kind = MemoryDocumentKind.USER_FACT.name,
                    category = MemoryCategory.ROUTINE.name,
                    source = MemoryDocumentSource.TOOL.name,
                    confidence = 0.9f,
                    createdAt = now,
                    updatedAt = now,
                ),
            ),
            temporalScope = TemporalScope.WEEK,
        )

        assertTrue(block.contains("ROUTINE DICHIARATE:"))
        assertTrue(block.contains("Martedì e giovedì fa tapis roulant"))
        assertTrue(block.contains("FATTI:"))
        assertTrue(block.contains("L'utente si chiama Francesco"))
        assertFalse(block.substringAfter("FATTI:").contains("tapis"))
    }

    private fun userFact(value: String): MemoryDocumentEntity =
        MemoryDocumentEntity(
            id = 1L,
            value = value,
            kind = MemoryDocumentKind.USER_FACT.name,
            category = MemoryCategory.IDENTITY.name,
            source = MemoryDocumentSource.TOOL.name,
            confidence = 0.9f,
            createdAt = now,
            updatedAt = now,
        )
}
