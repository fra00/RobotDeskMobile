package com.example.mydeskrobot.data.predictivity

import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.domain.predictivity.HabitSlotEligibility
import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository

class HabitSlotRepository(
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
) {
    suspend fun upsert(slot: HabitSlot): Long =
        unifiedMemoryRepository.upsertHabitSlot(
            externalRef = HabitSlotCodec.externalRef(slot.slotKey),
            value = HabitSlotCodec.encode(slot),
            confidence = slot.confidence,
            ttlDays = ProactivityConstants.PATTERN_TTL_DAYS,
        )

    suspend fun findBySlotKey(slotKey: String): HabitSlot? {
        val doc = unifiedMemoryRepository.getByExternalRef(HabitSlotCodec.externalRef(slotKey))
            ?: return null
        return HabitSlotCodec.decode(doc.value)
    }

    suspend fun listAll(): List<HabitSlot> =
        unifiedMemoryRepository.getToolByCategory(MemoryCategory.PATTERN, limit = 200)
            .mapNotNull { HabitSlotCodec.decode(it.value) }
            .filter { it.slotKey.isNotBlank() }

    suspend fun listEligibleForDeviation(): List<HabitSlot> =
        listAll().filter { HabitSlotEligibility.isEligibleForDeviation(it) }

    suspend fun deleteBySlotKey(slotKey: String): Boolean {
        val doc = unifiedMemoryRepository.getByExternalRef(HabitSlotCodec.externalRef(slotKey))
            ?: return false
        return unifiedMemoryRepository.deleteById(doc.id)
    }

    suspend fun deleteByCanonical(canonicalLabel: String): Int {
        val normalized = canonicalLabel.trim()
        if (normalized.isBlank()) return 0
        val matches = listAll().filter {
            it.canonicalLabel.equals(normalized, ignoreCase = true)
        }
        var deleted = 0
        matches.forEach { slot ->
            if (deleteBySlotKey(slot.slotKey)) deleted++
        }
        return deleted
    }
}
