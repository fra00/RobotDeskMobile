package com.example.mydeskrobot.domain.predictivity

import com.example.mydeskrobot.domain.proactive.ProactivityConstants
import kotlin.math.abs
import kotlin.math.min

/**
 * Local canonical cleanup + meal-by-clock refinement (Kotlin SSOT).
 * Complements the LLM normalize pass in the hybrid miner.
 */
object HabitCanonicalPolicy {

    private val mealTokens = setOf(
        "pasto",
        "pranzo",
        "cena",
        "colazione",
        "mangiare",
        "mangiare_qualcosa",
        "qualcosa_da_mangiare",
        "spuntino",
        "aperitivo",
        "breakfast",
        "lunch",
        "dinner",
        "meal",
    )

    private val aliasExact = mapOf(
        "passeggiata_con_il_cane" to "passeggiata_cane",
        "passeggiata_del_cane" to "passeggiata_cane",
        "camminata_cane" to "passeggiata_cane",
        "walk_dog" to "passeggiata_cane",
        "riunione_lavoro" to "riunione",
        "meeting" to "riunione",
        "call_lavoro" to "riunione",
        "tapis" to "tapis_roulant",
        "tapisroulant" to "tapis_roulant",
        "palestra_gym" to "palestra",
        "gym" to "palestra",
    )

    fun applyLocalAliases(canonical: String): String {
        val key = canonical.trim().lowercase().replace(' ', '_')
        if (key.isBlank()) return "activity"
        aliasExact[key]?.let { return it }
        // Collapse "mangiare_*" / vague eat phrases into meal family before time refine.
        if (key.startsWith("mangiare") || key.contains("da_mangiare") || key == "qualcosa") {
            return "pasto"
        }
        return key
    }

    fun isMealFamily(canonical: String): Boolean {
        val key = applyLocalAliases(canonical)
        if (key in mealTokens) return true
        return mealTokens.any { token -> key == token || key.startsWith("${token}_") }
    }

    /**
     * Prefer clock band over the written word ("pranzo" at 21:50 → cena).
     */
    fun refineMealByTime(
        canonical: String,
        minutesSinceMidnight: Int,
        dayMinutes: Int = 24 * 60,
    ): String {
        val base = applyLocalAliases(canonical)
        if (!isMealFamily(base)) return base
        val m = minutesSinceMidnight.mod(dayMinutes)
        return when {
            m in (5 * 60)..(10 * 60 + 30) -> "colazione"
            m in (11 * 60)..(15 * 60 + 30) -> "pranzo"
            m in (17 * 60)..(dayMinutes - 1) || m < (3 * 60) -> "cena"
            else -> "pasto"
        }
    }
}

/**
 * Merge slots that share a canonical label and fall in a nearby time window.
 */
object HabitSlotMergePolicy {

    fun circularMinutesDelta(a: Int, b: Int, dayMinutes: Int = 24 * 60): Int {
        val d = abs(a.mod(dayMinutes) - b.mod(dayMinutes))
        return min(d, dayMinutes - d)
    }

    fun areTimeCompatible(
        minutesA: Int,
        minutesB: Int,
        windowMinutes: Int = ProactivityConstants.PREDICTIVITY_MERGE_WINDOW_MINUTES,
    ): Boolean = circularMinutesDelta(minutesA, minutesB) <= windowMinutes

    fun findCompatible(
        candidates: List<HabitSlot>,
        episodeMinutes: Int,
        windowMinutes: Int = ProactivityConstants.PREDICTIVITY_MERGE_WINDOW_MINUTES,
    ): HabitSlot? =
        candidates
            .filter { areTimeCompatible(it.typicalTimeMinutes, episodeMinutes, windowMinutes) }
            .minByOrNull { circularMinutesDelta(it.typicalTimeMinutes, episodeMinutes) }

    /**
     * Cluster same-canonical slots whose typical times fall in one merge window (greedy).
     */
    fun clusterByTime(
        slots: List<HabitSlot>,
        windowMinutes: Int = ProactivityConstants.PREDICTIVITY_MERGE_WINDOW_MINUTES,
    ): List<List<HabitSlot>> {
        if (slots.isEmpty()) return emptyList()
        val remaining = slots.sortedBy { it.typicalTimeMinutes }.toMutableList()
        val clusters = mutableListOf<List<HabitSlot>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val cluster = mutableListOf(seed)
            var changed = true
            while (changed) {
                changed = false
                val iterator = remaining.iterator()
                while (iterator.hasNext()) {
                    val candidate = iterator.next()
                    val fits = cluster.any {
                        areTimeCompatible(it.typicalTimeMinutes, candidate.typicalTimeMinutes, windowMinutes)
                    }
                    if (fits) {
                        cluster.add(candidate)
                        iterator.remove()
                        changed = true
                    }
                }
            }
            clusters.add(cluster)
        }
        return clusters
    }

    fun mergeCluster(members: List<HabitSlot>): HabitSlot {
        require(members.isNotEmpty())
        if (members.size == 1) return members.first()

        val canonical = members.first().canonicalLabel
        val totalHits = members.sumOf { it.hitCount.coerceAtLeast(0) }
        val weightedTypical = if (totalHits <= 0) {
            members.map { it.typicalTimeMinutes }.average().toInt()
        } else {
            members.sumOf { it.typicalTimeMinutes * it.hitCount.coerceAtLeast(1) } /
                members.sumOf { it.hitCount.coerceAtLeast(1) }
        }
        val tolerance = members.maxOf { it.timeToleranceMinutes }
        val display = members
            .maxWithOrNull(compareBy<HabitSlot> { it.hitCount }.thenByDescending { it.displayLabel.length })
            ?.displayLabel
            ?: HabitLabelDisplay.fromCanonical(canonical)
        val raw = members.flatMap { it.rawLabels }.toSet()
        val lastHit = members.mapNotNull { it.lastHitDayKey }.maxOrNull()
        val slotKey = HabitSlotKey.buildSlotKey(
            canonical,
            HabitSlotKey.timeBucketMinutesFromClock(weightedTypical),
        )
        return HabitSlot(
            slotKey = slotKey,
            canonicalLabel = canonical,
            displayLabel = display,
            typicalTimeMinutes = weightedTypical,
            timeToleranceMinutes = tolerance,
            hitCount = totalHits,
            lastHitDayKey = lastHit,
            confidence = HabitSlotConfidence.confidenceForHitCount(totalHits),
            rawLabels = raw,
            source = "activity_log_miner",
        )
    }
}

/** Avoid depending on integration layer from domain display helpers. */
internal object HabitLabelDisplay {
    fun fromCanonical(canonical: String): String =
        canonical.replace('_', ' ')
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
