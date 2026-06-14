package com.example.mydeskrobot.memory

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryDao
import com.example.mydeskrobot.memory.db.MemoryDatabase
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import java.util.concurrent.TimeUnit

class UserMemoryRepository(
    private val dao: MemoryDao,
) {
    suspend fun upsert(
        category: MemoryCategory,
        value: String,
        confidence: Float,
        sourceMessageId: Long,
        expiresAt: Long? = null,
    ): Long {
        require(MemoryCategory.isUserFacing(category)) {
            "Use upsertAutonomy for robot-internal categories"
        }
        val normalized = value.trim()
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        val existing = dao.findExact(category, normalized)
            ?: findSemanticDuplicate(category, normalized)
        val item = if (existing != null) {
            existing.copy(
                confidence = maxOf(existing.confidence, confidence),
                updatedAt = now,
                sourceMessageId = sourceMessageId,
                isDeleted = false,
                expiresAt = null,
            )
        } else {
            MemoryItemEntity(
                category = category,
                value = normalized,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = now,
                updatedAt = now,
                sourceMessageId = sourceMessageId,
                expiresAt = expiresAt,
            )
        }
        return dao.upsert(item)
    }

    suspend fun upsertAutonomy(
        category: MemoryCategory,
        value: String,
        confidence: Float = 0.85f,
        sourceMessageId: Long = SOURCE_MESSAGE_LLM_TOOL,
        ttlDays: Int? = null,
    ): AutonomyUpsertResult {
        require(MemoryCategory.isRobotInternal(category)) {
            "upsertAutonomy is only for OBSERVATION, INTENT, PATTERN"
        }
        val normalized = value.trim()
        if (normalized.isBlank()) return AutonomyUpsertResult.InvalidValue

        if (category == MemoryCategory.INTENT &&
            dao.countActiveByCategory(MemoryCategory.INTENT) >= MAX_ACTIVE_INTENTS
        ) {
            val existing = dao.findExact(MemoryCategory.INTENT, normalized)
            if (existing == null) {
                return AutonomyUpsertResult.IntentCapReached
            }
        }

        val now = System.currentTimeMillis()
        val effectiveTtlDays = ttlDays ?: defaultTtlDays(category)
        val expiresAt = now + TimeUnit.DAYS.toMillis(effectiveTtlDays.toLong())

        val existing = when (category) {
            MemoryCategory.INTENT -> dao.findExact(MemoryCategory.INTENT, normalized)
            MemoryCategory.OBSERVATION -> findObservationDuplicate(normalized)
            else -> dao.findExact(category, normalized)
        }

        val item = if (existing != null) {
            existing.copy(
                value = normalized,
                confidence = maxOf(existing.confidence, confidence.coerceIn(0f, 1f)),
                updatedAt = now,
                sourceMessageId = sourceMessageId,
                isDeleted = false,
                expiresAt = expiresAt,
            )
        } else {
            MemoryItemEntity(
                category = category,
                value = normalized,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = now,
                updatedAt = now,
                sourceMessageId = sourceMessageId,
                expiresAt = expiresAt,
            )
        }
        return AutonomyUpsertResult.Success(dao.upsert(item))
    }

    suspend fun pruneExpired(): Int {
        val now = System.currentTimeMillis()
        return dao.softDeleteExpired(now)
    }

    suspend fun countActiveIntents(): Int =
        dao.countActiveByCategory(MemoryCategory.INTENT)

    suspend fun getActiveIntents(limit: Int = MAX_ACTIVE_INTENTS): List<MemoryItemEntity> =
        dao.getByCategory(MemoryCategory.INTENT, limit.coerceAtMost(MAX_ACTIVE_INTENTS))

    suspend fun getRecentObservations(
        limit: Int = DEFAULT_OBSERVATION_LIMIT,
        maxAgeDays: Int = DEFAULT_OBSERVATION_TTL_DAYS,
    ): List<MemoryItemEntity> {
        val items = dao.getByCategory(MemoryCategory.OBSERVATION, limit.coerceAtLeast(1))
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
        return items.filter { it.updatedAt >= cutoff }
    }

    suspend fun getActivePatterns(limit: Int = DEFAULT_PATTERN_LIMIT): List<MemoryItemEntity> =
        dao.getByCategory(MemoryCategory.PATTERN, limit.coerceAtLeast(1))

    suspend fun getUserFacingActive(): List<MemoryItemEntity> =
        dao.getUserFacingActive(MemoryCategory.USER_FACING.toList())

    suspend fun getCoreIdentity(limit: Int = 2): List<MemoryItemEntity> =
        getByCategory(MemoryCategory.IDENTITY, limit)

    suspend fun getByCategory(category: MemoryCategory, limit: Int): List<MemoryItemEntity> =
        dao.getByCategory(category, limit)

    suspend fun searchRelevant(
        query: String,
        limit: Int,
        includeRobotInternal: Boolean = false,
    ): List<MemoryItemEntity> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val active = if (includeRobotInternal) {
            dao.getAllActive()
        } else {
            dao.getUserFacingActive(MemoryCategory.USER_FACING.toList())
        }
        val ranked = MemoryTopicMatcher.rank(q, active, limit)
        if (ranked.isNotEmpty()) return ranked.map { it.item }
        val fallback = MemoryTopicMatcher.fallbackMatches(q, active).take(limit)
        if (fallback.isNotEmpty()) return fallback.map { it.item }
        return if (includeRobotInternal) {
            dao.searchByQuery(q, limit)
        } else {
            searchUserFacingByQuery(q, limit)
        }
    }

    private suspend fun searchUserFacingByQuery(query: String, limit: Int): List<MemoryItemEntity> {
        val merged = linkedMapOf<Long, MemoryItemEntity>()
        for (category in MemoryCategory.USER_FACING) {
            dao.searchByQuery(category, query, limit).forEach { merged.putIfAbsent(it.id, it) }
            if (merged.size >= limit) break
        }
        return merged.values.take(limit).toList()
    }

    /**
     * Expands common entity tokens before fuzzy search (e.g. cane → animale).
     */
    suspend fun searchRelevantExpanded(query: String, limit: Int): List<MemoryItemEntity> {
        val expanded = expandQueryForSearch(query)
        val merged = linkedMapOf<Long, MemoryItemEntity>()
        for (q in expanded) {
            searchRelevant(q, limit, includeRobotInternal = false).forEach { item ->
                merged.putIfAbsent(item.id, item)
            }
            if (merged.size >= limit) break
        }
        return merged.values.take(limit).toList()
    }

    suspend fun getByCategories(
        categories: List<MemoryCategory>,
        limitPerCategory: Int,
    ): List<MemoryItemEntity> {
        val merged = linkedMapOf<Long, MemoryItemEntity>()
        for (category in categories) {
            getByCategory(category, limitPerCategory).forEach { item ->
                merged.putIfAbsent(item.id, item)
            }
        }
        return merged.values.toList()
    }

    /**
     * Compact entity catalog for vision: FACT + ROUTINE, deduplicated by normalized value.
     */
    suspend fun getVisionCatalog(limit: Int = 18): List<MemoryItemEntity> {
        val facts = getByCategory(MemoryCategory.FACT, limit)
        val routines = getByCategory(MemoryCategory.ROUTINE, limit)
        val combined = (facts + routines)
            .sortedWith(
                compareByDescending<MemoryItemEntity> { it.confidence }
                    .thenByDescending { it.updatedAt },
            )
        return deduplicateByNormalizedValue(combined).take(limit)
    }

    private fun expandQueryForSearch(query: String): List<String> {
        val base = query.trim()
        if (base.isBlank()) return emptyList()
        val tokens = MemoryTopicMatcher.tokenize(base)
        val expansions = linkedSetOf(base)
        val entityExpansions = mapOf(
            "cane" to listOf("animale", "cane nome", "cane"),
            "gatto" to listOf("animale", "gatto nome", "gatto"),
            "laboratorio" to listOf("laboratorio", "stanza", "ufficio"),
        )
        for (token in tokens) {
            entityExpansions[token]?.forEach { expansions += it }
        }
        return expansions.toList()
    }

    private fun deduplicateByNormalizedValue(items: List<MemoryItemEntity>): List<MemoryItemEntity> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<MemoryItemEntity>()
        for (item in items) {
            val key = item.value.trim().lowercase()
            if (seen.add(key)) {
                result += item
            }
        }
        return result
    }

    suspend fun getAllActive(): List<MemoryItemEntity> = dao.getAllActive()

    suspend fun markUsed(items: List<MemoryItemEntity>) {
        if (items.isEmpty()) return
        dao.markUsed(items.map { it.id }, System.currentTimeMillis())
    }

    suspend fun forgetByText(query: String): Int =
        forgetByTopic(query).deletedCount

    /**
     * Soft-deletes all active memories that match the topic (token overlap), not exact stored text.
     */
    suspend fun forgetByTopic(query: String): ForgetByTopicResult {
        val q = query.trim()
        if (q.isBlank()) return ForgetByTopicResult(0, emptyList(), emptyList())

        val active = dao.getAllActive()
        var matches = MemoryTopicMatcher.rank(q, active)
            .filter { it.score >= MemoryTopicMatcher.MIN_FORGET_SCORE }

        if (matches.isEmpty()) {
            matches = MemoryTopicMatcher.fallbackMatches(q, active)
                .filter { it.score >= 0.34f }
        }

        if (matches.isEmpty()) {
            val likeDeleted = dao.softDeleteByText(q, System.currentTimeMillis())
            if (likeDeleted > 0) {
                return ForgetByTopicResult(likeDeleted, listOf(q), emptyList())
            }
            return ForgetByTopicResult(0, emptyList(), emptyList())
        }

        val now = System.currentTimeMillis()
        val values = mutableListOf<String>()
        val ids = mutableListOf<Long>()
        matches.forEach { scored ->
            dao.softDeleteById(scored.item.id, now)
            values += scored.item.value
            ids += scored.item.id
        }
        return ForgetByTopicResult(values.size, values, ids)
    }

    suspend fun updateValue(id: Long, value: String): Boolean {
        val normalized = value.trim()
        if (id <= 0L || normalized.isBlank()) return false
        val updated = dao.updateValue(id, normalized, System.currentTimeMillis())
        return updated > 0
    }

    suspend fun deleteById(id: Long): Boolean {
        if (id <= 0L) return false
        val existing = dao.findActiveById(id) ?: return false
        dao.softDeleteById(existing.id, System.currentTimeMillis())
        return true
    }

    suspend fun resetMemory() {
        dao.clearAll()
    }

    /** Clears only durable user-facing memories; keeps robot-internal autonomy rows. */
    suspend fun resetUserFacingMemory() {
        dao.clearByCategories(MemoryCategory.USER_FACING.toList())
    }

    suspend fun pruneIfNeeded(maxItems: Int): Int {
        pruneExpired()
        val active = dao.countActive()
        if (active <= maxItems) return 0
        val toDelete = active - maxItems
        val lowPriority = dao.lowPriorityForPruning(
            excludeCategories = MemoryCategory.ROBOT_INTERNAL.toList(),
            limit = toDelete,
        )
        val now = System.currentTimeMillis()
        lowPriority.forEach { dao.softDeleteById(it.id, now) }
        return lowPriority.size
    }

    suspend fun reorganize(): Int {
        val active = dao.getUserFacingActive(MemoryCategory.USER_FACING.toList())
        val toDelete = mutableSetOf<Long>()
        val now = System.currentTimeMillis()

        active.groupBy { it.category }.values.forEach { categoryItems ->
            val remaining = categoryItems.filter { it.id !in toDelete }
            for (i in remaining.indices) {
                val anchor = remaining[i]
                if (anchor.id in toDelete) continue
                val cluster = mutableListOf(anchor)
                for (j in i + 1 until remaining.size) {
                    val other = remaining[j]
                    if (other.id in toDelete) continue
                    if (MemoryDuplicateDetector.areDuplicates(anchor.value, other.value, anchor.category)) {
                        cluster += other
                    }
                }
                if (cluster.size <= 1) continue
                val keeper = cluster.maxWithOrNull(
                    compareBy<MemoryItemEntity> { it.confidence }
                        .thenBy { it.updatedAt },
                ) ?: continue
                cluster.filter { it.id != keeper.id }.forEach { toDelete += it.id }
            }
        }

        toDelete.forEach { dao.softDeleteById(it, now) }
        return toDelete.size
    }

    private suspend fun findSemanticDuplicate(
        category: MemoryCategory,
        value: String,
    ): MemoryItemEntity? =
        dao.getByCategory(category, limit = 200)
            .filter { MemoryDuplicateDetector.areDuplicates(value, it.value, category) }
            .maxWithOrNull(
                compareBy<MemoryItemEntity> { it.confidence }
                    .thenBy { it.updatedAt },
            )

    private suspend fun findObservationDuplicate(value: String): MemoryItemEntity? {
        val dateKey = extractDateKey(value) ?: return null
        val observations = dao.getByCategory(MemoryCategory.OBSERVATION, limit = 50)
        return observations.firstOrNull { extractDateKey(it.value) == dateKey }
    }

    private fun extractDateKey(value: String): String? {
        val match = DATE_KEY_REGEX.find(value.lowercase()) ?: return null
        return match.value
    }

    private fun defaultTtlDays(category: MemoryCategory): Int = when (category) {
        MemoryCategory.OBSERVATION -> DEFAULT_OBSERVATION_TTL_DAYS
        MemoryCategory.INTENT -> DEFAULT_INTENT_TTL_DAYS
        MemoryCategory.PATTERN -> DEFAULT_PATTERN_TTL_DAYS
        else -> DEFAULT_OBSERVATION_TTL_DAYS
    }

    companion object {
        /** Marks facts saved explicitly via LLM tool (not conversation log extraction). */
        const val SOURCE_MESSAGE_LLM_TOOL: Long = -1L

        const val MAX_ACTIVE_INTENTS = 3
        const val DEFAULT_OBSERVATION_TTL_DAYS = 7
        const val DEFAULT_INTENT_TTL_DAYS = 1
        const val DEFAULT_PATTERN_TTL_DAYS = 30
        const val DEFAULT_OBSERVATION_LIMIT = 8
        const val DEFAULT_PATTERN_LIMIT = 3

        private val DATE_KEY_REGEX = Regex("""\d{1,2}\s+\w+\s+\d{4}""")

        fun create(context: Context): UserMemoryRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                MemoryDatabase::class.java,
                "user_memory.db",
            )
                .addMigrations(MemoryDatabase.MIGRATION_1_2)
                .build()
            return UserMemoryRepository(db.memoryDao())
        }

        /** For unit tests with an in-memory or fake [MemoryDao]. */
        fun createForTest(dao: MemoryDao): UserMemoryRepository = UserMemoryRepository(dao)
    }
}
