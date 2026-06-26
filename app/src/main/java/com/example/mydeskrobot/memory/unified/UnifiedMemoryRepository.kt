package com.example.mydeskrobot.memory.unified

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.lists.ListItemRepository
import com.example.mydeskrobot.data.lists.db.ListItemEntity
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.scheduled.db.ScheduledTaskEntity
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.domain.spatial.SpatialPlace
import com.example.mydeskrobot.memory.MemorySafetyPinDetector
import com.example.mydeskrobot.memory.MemorySettingsRepository
import com.example.mydeskrobot.memory.MemoryDuplicateDetector
import com.example.mydeskrobot.memory.MemoryTopicMatcher
import com.example.mydeskrobot.memory.AutonomyUpsertResult
import com.example.mydeskrobot.memory.ForgetByTopicResult
import com.example.mydeskrobot.memory.UserMemoryRepository
import com.example.mydeskrobot.memory.consolidate.ConsolidatedMemoryLine
import com.example.mydeskrobot.memory.consolidate.MemoryConsolidationApplicator
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.db.MemoryItemEntity
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentDao
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentDatabase
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.memory.unified.embedding.NoOpTextEmbedder
import com.example.mydeskrobot.memory.unified.embedding.TextEmbedder
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import java.text.SimpleDateFormat
import java.util.Locale
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UnifiedMemoryRepository(
    private val dao: MemoryDocumentDao,
    private val migrationRunner: suspend () -> Unit,
    private val textEmbedderProvider: () -> TextEmbedder = { NoOpTextEmbedder },
) {
    private val textEmbedder: TextEmbedder
        get() = textEmbedderProvider()

    suspend fun ensureMigrated() {
        migrationRunner()
    }

    private suspend fun upsertDocument(entity: MemoryDocumentEntity): Long =
        dao.upsert(withEmbedding(entity))

    private suspend fun updateDocument(entity: MemoryDocumentEntity) {
        dao.update(withEmbedding(entity))
    }

    private suspend fun withEmbedding(entity: MemoryDocumentEntity): MemoryDocumentEntity {
        if (!shouldEmbed(entity)) return entity
        val vector = textEmbedder.embed(entity.value.trim()) ?: return entity
        return entity.copy(embedding = EmbeddingCodec.encode(vector))
    }

    private suspend fun embedQuery(query: String): FloatArray? {
        val q = query.trim()
        if (q.isBlank() || !textEmbedder.isAvailable) return null
        return textEmbedder.embed(q)
    }

    private fun shouldEmbed(entity: MemoryDocumentEntity): Boolean {
        if (!textEmbedder.isAvailable) return false
        if (!entity.isActive) return false
        if (entity.value.isBlank()) return false
        return entity.kind in EMBEDDABLE_KINDS
    }

    suspend fun reindexMissingEmbeddings(limit: Int = 32): Int {
        ensureMigrated()
        if (!textEmbedder.isAvailable) return 0
        val docs = dao.getAllActive()
            .filter { doc -> doc.embedding == null && shouldEmbed(doc) }
            .take(limit.coerceAtLeast(1))
        var updated = 0
        for (doc in docs) {
            val embedded = withEmbedding(doc)
            if (embedded.embedding != null) {
                dao.update(embedded)
                updated++
            }
        }
        return updated
    }

    suspend fun saveUserFact(
        value: String,
        category: MemoryCategory,
        confidence: Float,
        source: MemoryDocumentSource,
        expiresAt: Long? = null,
        legacyId: Long? = null,
    ): Long {
        ensureMigrated()
        val normalized = value.trim()
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        val kind = if (MemoryCategory.isRobotInternal(category)) {
            MemoryDocumentKind.AUTONOMY
        } else {
            MemoryDocumentKind.USER_FACT
        }
        val externalRef = legacyId?.let { "legacy_memory:$it" }
        val existing = externalRef?.let { dao.getByExternalRef(it) }
        val entity = if (existing != null) {
            existing.copy(
                value = normalized,
                category = category.name,
                confidence = maxOf(existing.confidence, confidence.coerceIn(0f, 1f)),
                updatedAt = now,
                expiresAt = expiresAt,
                isActive = true,
            )
        } else {
            MemoryDocumentEntity(
                value = normalized,
                kind = kind.name,
                category = category.name,
                source = source.name,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = now,
                updatedAt = now,
                expiresAt = expiresAt,
                externalRef = externalRef,
            )
        }
        return upsertDocument(entity)
    }

    suspend fun saveReminderProjection(
        taskId: Long,
        message: String,
        triggerAtMillis: Long,
        source: MemoryDocumentSource = MemoryDocumentSource.TOOL,
    ): Long {
        ensureMigrated()
        val normalized = message.trim()
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        val scheduledDayKey = dayKeyFor(triggerAtMillis)
        val externalRef = reminderExternalRef(taskId)
        val existing = dao.getByExternalRef(externalRef)
        val entity = if (existing != null) {
            existing.copy(
                value = normalized,
                scheduledDayKey = scheduledDayKey,
                scheduledAtMs = triggerAtMillis,
                updatedAt = now,
                isActive = true,
            )
        } else {
            MemoryDocumentEntity(
                value = normalized,
                kind = MemoryDocumentKind.REMINDER.name,
                category = null,
                source = source.name,
                confidence = 1f,
                createdAt = now,
                updatedAt = now,
                scheduledDayKey = scheduledDayKey,
                scheduledAtMs = triggerAtMillis,
                externalRef = externalRef,
            )
        }
        return upsertDocument(entity)
    }

    suspend fun deactivateReminderProjection(taskId: Long) {
        ensureMigrated()
        dao.deactivateByExternalRef(reminderExternalRef(taskId))
    }

    suspend fun verifyProjection(externalRef: String, expectedActive: Boolean): Boolean {
        ensureMigrated()
        val doc = dao.getByExternalRef(externalRef) ?: return !expectedActive
        if (doc.isActive != expectedActive) return false
        return if (expectedActive) doc.value.isNotBlank() else true
    }

    suspend fun isEpisodeProjectionCurrent(
        eventId: Long,
        label: String,
        rawPhrase: String?,
    ): Boolean {
        ensureMigrated()
        val doc = dao.getByExternalRef(activityLogExternalRef(eventId)) ?: return false
        if (!doc.isActive) return false
        return doc.value == EpisodeProjectionValue.format(label, rawPhrase)
    }

    suspend fun saveListItemProjection(
        itemId: Long,
        type: ListItemType,
        text: String,
        checked: Boolean,
        source: MemoryDocumentSource = MemoryDocumentSource.TOOL,
    ): Long {
        ensureMigrated()
        val normalized = text.trim()
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        val active = type == ListItemType.NOTE || !checked
        return upsertByExternalRef(
            externalRef = listItemExternalRef(itemId),
            value = normalized,
            kind = MemoryDocumentKind.LIST_ITEM,
            category = type.name,
            source = source,
            createdAt = now,
            updatedAt = now,
            isActive = active,
        )
    }

    suspend fun deactivateListItemProjection(itemId: Long) {
        ensureMigrated()
        dao.deactivateByExternalRef(listItemExternalRef(itemId))
    }

    suspend fun saveEpisodeProjection(
        eventId: Long,
        label: String,
        eventKind: EpisodeKind,
        dayKey: String,
        timestampMs: Long,
        confidence: EpisodeConfidence = EpisodeConfidence.CONFIRMED,
        scheduledDayKey: String? = null,
        scheduledAtMs: Long? = null,
        actor: String? = null,
        sourceChannel: String? = null,
        rawPhrase: String? = null,
        source: MemoryDocumentSource = MemoryDocumentSource.TOOL,
        isUnread: Boolean = false,
        externalRefOverride: String? = null,
        linkedActivityLogId: Long? = null,
    ): Long {
        ensureMigrated()
        val normalized = EpisodeProjectionValue.format(label, rawPhrase)
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        val targetDayKey = scheduledDayKey?.trim()?.takeIf { it.isNotBlank() } ?: dayKey
        val expiresAt = timestampMs + EPISODE_RETENTION_MS
        val externalRef = externalRefOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: activityLogExternalRef(eventId)
        return upsertByExternalRef(
            externalRef = externalRef,
            value = normalized,
            kind = MemoryDocumentKind.EPISODE,
            category = eventKind.name,
            source = source,
            confidence = if (confidence == EpisodeConfidence.CONFIRMED) 1f else 0.6f,
            createdAt = timestampMs,
            updatedAt = now,
            expiresAt = expiresAt,
            dayKey = dayKey,
            scheduledDayKey = targetDayKey,
            scheduledAtMs = scheduledAtMs,
            actor = actor?.trim()?.takeIf { it.isNotBlank() },
            sourceChannel = sourceChannel?.trim()?.takeIf { it.isNotBlank() },
            episodeConfidence = confidence.name,
            isUnread = isUnread,
            linkedActivityLogId = linkedActivityLogId ?: eventId,
        )
    }

    suspend fun getByExternalRef(externalRef: String): MemoryDocumentEntity? {
        ensureMigrated()
        return dao.getByExternalRef(externalRef)
    }

    suspend fun markEpisodeRead(externalRef: String) {
        ensureMigrated()
        dao.markReadByExternalRef(externalRef)
    }

    suspend fun listUnreadNotificationEpisodes(limit: Int = 50): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getUnreadByKind(MemoryDocumentKind.EPISODE.name, limit)
    }

    suspend fun listUnreadNotificationExternalRefs(): List<String> =
        listUnreadNotificationEpisodes()
            .mapNotNull { it.externalRef }
            .filter { it.startsWith(NOTIFICATION_REF_PREFIX) }

    suspend fun markAllUnreadNotificationEpisodesRead(): Int {
        ensureMigrated()
        val refs = listUnreadNotificationExternalRefs()
        refs.forEach { dao.markReadByExternalRef(it) }
        return refs.size
    }

    suspend fun saveSpatialPlaceProjection(
        placeId: Long,
        label: String,
        landmarks: List<String>,
        roomType: String?,
        description: String?,
        source: MemoryDocumentSource = MemoryDocumentSource.TOOL,
    ): Long {
        ensureMigrated()
        val normalizedLabel = label.trim()
        if (normalizedLabel.isBlank()) return -1L
        val landmarkText = landmarks.filter { it.isNotBlank() }.joinToString(", ")
        val value = buildString {
            append(normalizedLabel)
            if (landmarkText.isNotBlank()) append(": ").append(landmarkText)
            val desc = description?.trim().orEmpty()
            if (desc.isNotBlank()) append(" — ").append(desc)
        }
        val now = System.currentTimeMillis()
        return upsertByExternalRef(
            externalRef = spatialPlaceExternalRef(placeId),
            value = value,
            kind = MemoryDocumentKind.SPATIAL,
            category = roomType?.trim()?.takeIf { it.isNotBlank() },
            source = source,
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun saveCurrentPlaceProjection(
        placeId: Long?,
        label: String?,
        roomType: String?,
        confidence: Float,
        source: MemoryDocumentSource = MemoryDocumentSource.TOOL,
    ): Long {
        ensureMigrated()
        val now = System.currentTimeMillis()
        if (placeId == null || label.isNullOrBlank()) {
            dao.deactivateByExternalRef(SPATIAL_CURRENT_EXTERNAL_REF, now)
            return -1L
        }
        val value = "Stanza corrente: ${label.trim()}"
        return upsertByExternalRef(
            externalRef = SPATIAL_CURRENT_EXTERNAL_REF,
            value = value,
            kind = MemoryDocumentKind.SPATIAL,
            category = roomType?.trim()?.takeIf { it.isNotBlank() } ?: "current",
            source = source,
            confidence = confidence.coerceIn(0f, 1f),
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun listActiveByKind(
        kind: MemoryDocumentKind,
        limit: Int = 50,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getActiveByKind(kind.name, limit)
    }

    suspend fun searchRelevant(
        query: String,
        limit: Int = 20,
        filters: MemoryDocumentFilters = MemoryDocumentFilters(),
        minScore: Float = MemorySearchScorer.DEFAULT_MIN_SCORE,
        queryEmbedding: FloatArray? = null,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        val now = System.currentTimeMillis()
        val candidates = loadCandidates(filters, now)
        if (candidates.isEmpty()) return emptyList()
        val q = query.trim()
        if (q.isBlank()) {
            return candidates
                .sortedWith(
                    compareByDescending<MemoryDocumentEntity> { it.useCount }
                        .thenByDescending { it.updatedAt },
                )
                .take(limit)
        }
        val resolvedQueryEmbedding = queryEmbedding ?: embedQuery(q)
        return MemorySearchScorer.rank(
            query = q,
            documents = candidates,
            limit = limit,
            minScore = minScore,
            queryEmbedding = resolvedQueryEmbedding,
        ).map { it.document }
    }

    suspend fun listEpisodesForDay(dayKey: String): List<MemoryDocumentEntity> {
        ensureMigrated()
        val normalizedDayKey = dayKey.trim()
        if (normalizedDayKey.isBlank()) return emptyList()
        return dao.getAllActive()
            .filter { doc ->
                doc.kind == MemoryDocumentKind.EPISODE.name &&
                    (doc.dayKey == normalizedDayKey || doc.scheduledDayKey == normalizedDayKey)
            }
            .sortedBy { it.scheduledAtMs ?: it.createdAt }
    }

    suspend fun listEpisodesSinceDayKey(
        sinceDayKey: String,
        limit: Int,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getAllActive()
            .filter { doc ->
                doc.kind == MemoryDocumentKind.EPISODE.name &&
                    (doc.dayKey ?: "") >= sinceDayKey
            }
            .sortedByDescending { it.scheduledAtMs ?: it.createdAt }
            .take(limit)
    }

    suspend fun recallForQuestion(request: MemoryRecallRequest): List<MemoryDocumentEntity> {
        ensureMigrated()
        val now = System.currentTimeMillis()
        val merged = linkedMapOf<Long, MemorySearchScorer.ScoredDocument>()

        fun addDocument(document: MemoryDocumentEntity, score: Float) {
            val existing = merged[document.id]
            if (existing == null || score > existing.score) {
                merged[document.id] = MemorySearchScorer.ScoredDocument(document, score)
            }
        }

        listUnreadNotificationEpisodes(limit = MemoryRecallBudget.EPISODE_MAX_WIDE_RANGE)
            .forEach { episode ->
                addDocument(episode, MemoryRecallRequest.UNREAD_EPISODE_SCORE)
            }

        if (request.preferUserFacts) {
            val userFacts = getUserFacingActiveDocuments()
            val factQueries = request.searchQueries.ifEmpty {
                listOf(request.query.trim()).filter { it.isNotBlank() }
            }
            factQueries.forEach { searchQuery ->
                MemorySearchScorer.rank(
                    query = searchQuery,
                    documents = userFacts,
                    limit = MemoryRecallBudget.USER_FACT_MIN_DEFAULT + 10,
                    minScore = 0.12f,
                    queryEmbedding = embedQuery(searchQuery),
                ).forEach { scored ->
                    addDocument(
                        scored.document,
                        maxOf(scored.score, MemoryRecallRequest.USER_FACT_LINKED_SCORE),
                    )
                }
            }
        }

        when (request.temporalScope) {
            TemporalScope.SINGLE_DAY -> request.focusDayKey?.let { dayKey ->
                listEpisodesForDay(dayKey).forEach { episode ->
                    addDocument(episode, MemoryRecallRequest.SCOPE_LINKED_SCORE)
                }
                listByKindAndDay(MemoryDocumentKind.REMINDER, dayKey).forEach { reminder ->
                    addDocument(reminder, MemoryRecallRequest.SCOPE_LINKED_SCORE)
                }
            }
            TemporalScope.WEEK, TemporalScope.MONTH -> {
                if (request.includeHabitSummary &&
                    !request.preferEpisodicDetail &&
                    !request.preferUserFacts
                ) {
                    getHabitSummaryDocument()?.let { summary ->
                        addDocument(summary, MemoryRecallRequest.HABIT_SUMMARY_WIDE_RANGE_SCORE)
                    }
                }
                val daysBack = if (request.temporalScope == TemporalScope.WEEK) 7 else 31
                val calendar = java.util.Calendar.getInstance(Locale.ITALY)
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
                val sinceDayKey = dayKeyFor(calendar.timeInMillis)
                val episodeLimit = if (request.preferEpisodicDetail) {
                    MemoryRecallBudget.EPISODE_MAX_SINGLE_DAY
                } else {
                    MemoryRecallBudget.EPISODE_MAX_WIDE_RANGE
                }
                listEpisodesSinceDayKey(
                    sinceDayKey = sinceDayKey,
                    limit = episodeLimit,
                ).forEach { episode ->
                    addDocument(episode, MemoryRecallRequest.SCOPE_LINKED_SCORE - 0.05f)
                }
            }
            TemporalScope.NONE -> request.focusDayKey?.let { dayKey ->
                listEpisodesForDay(dayKey).forEach { episode ->
                    addDocument(episode, MemoryRecallRequest.SCOPE_LINKED_SCORE)
                }
                listByKindAndDay(MemoryDocumentKind.REMINDER, dayKey).forEach { reminder ->
                    addDocument(reminder, MemoryRecallRequest.SCOPE_LINKED_SCORE)
                }
            }
        }

        if (request.includeVisionCatalog) {
            getVisionCatalog(limit = 40).forEach { document ->
                addDocument(document, MemoryRecallRequest.VISION_CATALOG_SCORE)
            }
            getCoreIdentity(limit = 3).forEach { document ->
                addDocument(document, MemoryRecallRequest.VISION_CATALOG_SCORE)
            }
        }

        val allActive = dao.getAllActive(now)
        val searchableDocuments = if (request.excludeSpatialLandmarks || request.localizeQuery) {
            allActive.filter { doc ->
                doc.kind != MemoryDocumentKind.SPATIAL.name &&
                    !(request.preferEpisodicDetail && doc.kind == MemoryDocumentKind.HABIT_SUMMARY.name)
            }
        } else if (request.preferEpisodicDetail) {
            allActive.filter { it.kind != MemoryDocumentKind.HABIT_SUMMARY.name }
        } else if (request.preferUserFacts) {
            allActive.filter {
                it.kind != MemoryDocumentKind.HABIT_SUMMARY.name &&
                    it.kind != MemoryDocumentKind.EPISODE.name
            }
        } else {
            allActive
        }
        val semanticQueries = buildSemanticQueries(request)
        semanticQueries.forEach { searchQuery ->
            val queryEmbedding = embedQuery(searchQuery)
            MemorySearchScorer.rank(
                query = searchQuery,
                documents = searchableDocuments,
                limit = request.limit * 2,
                minScore = request.minScore,
                queryEmbedding = queryEmbedding,
            ).forEach { scored ->
                addDocument(scored.document, scored.score)
            }
        }

        if (request.includeHabitSummary &&
            (request.temporalScope == TemporalScope.SINGLE_DAY || request.focusDayKey != null) &&
            !request.preferEpisodicDetail &&
            !request.preferUserFacts
        ) {
            getHabitSummaryDocument()?.let { summary ->
                val primaryQuery = semanticQueries.firstOrNull().orEmpty()
                val summaryScore = if (primaryQuery.isNotBlank()) {
                    MemorySearchScorer.score(primaryQuery, summary, embedQuery(primaryQuery))
                } else {
                    0f
                }
                if (summaryScore >= request.minScore || request.focusDayKey != null) {
                    addDocument(summary, maxOf(summaryScore, request.minScore))
                }
            }
        }

        val ranked = merged.values.sortedByDescending { it.score }
        return applyRecallBudget(ranked, request).map { it.document }
    }

    private fun buildSemanticQueries(request: MemoryRecallRequest): List<String> {
        val fromPlan = request.searchQueries.map { it.trim() }.filter { it.isNotBlank() }
        if (fromPlan.isNotEmpty()) return fromPlan.distinct()
        val query = request.query.trim()
        if (query.isNotBlank()) return listOf(query)
        if (request.includeVisionCatalog) {
            return listOf("persone animali oggetti stanza laboratorio")
        }
        return emptyList()
    }

    private fun applyRecallBudget(
        ranked: List<MemorySearchScorer.ScoredDocument>,
        request: MemoryRecallRequest,
    ): List<MemorySearchScorer.ScoredDocument> {
        if (ranked.isEmpty()) return emptyList()
        val totalLimit = request.limit.coerceAtMost(MemoryRecallBudget.TOTAL)

        fun kindOf(doc: MemoryDocumentEntity): MemoryDocumentKind =
            runCatching { MemoryDocumentKind.valueOf(doc.kind) }
                .getOrDefault(MemoryDocumentKind.USER_FACT)

        val episodes = ranked.filter { kindOf(it.document) == MemoryDocumentKind.EPISODE }
        val nonEpisodes = ranked.filter { kindOf(it.document) != MemoryDocumentKind.EPISODE }

        return when {
            request.temporalScope == TemporalScope.SINGLE_DAY && request.focusDayKey != null -> {
                val episodeCap = MemoryRecallBudget.EPISODE_MAX_SINGLE_DAY
                val nonEpisodeMin = MemoryRecallBudget.NON_EPISODE_MIN_SINGLE_DAY
                val selectedEpisodes = episodes.take(episodeCap)
                val selectedNonEpisodes = nonEpisodes.take(maxOf(nonEpisodeMin, totalLimit - selectedEpisodes.size))
                val selectedIds = (selectedEpisodes + selectedNonEpisodes).map { it.document.id }.toSet()
                val remaining = totalLimit - selectedIds.size
                val filler = ranked.filter { it.document.id !in selectedIds }.take(maxOf(0, remaining))
                (selectedEpisodes + selectedNonEpisodes + filler).take(totalLimit)
            }
            request.temporalScope == TemporalScope.WEEK ||
                request.temporalScope == TemporalScope.MONTH -> {
                ranked.take(totalLimit)
            }
            else -> {
                val userFactMin = if (request.preferUserFacts) {
                    MemoryRecallBudget.USER_FACT_MIN_DEFAULT + 10
                } else {
                    MemoryRecallBudget.USER_FACT_MIN_DEFAULT
                }
                val userFacts = nonEpisodes.filter { kindOf(it.document) == MemoryDocumentKind.USER_FACT }
                val otherNonEpisodes = nonEpisodes.filter { kindOf(it.document) != MemoryDocumentKind.USER_FACT }
                val selectedUserFacts = userFacts.take(userFactMin)
                val episodeCap = maxOf(0, totalLimit - selectedUserFacts.size)
                val selectedEpisodes = episodes.take(episodeCap)
                val selectedIds = (selectedUserFacts + selectedEpisodes).map { it.document.id }.toSet()
                val remaining = totalLimit - selectedIds.size
                val filler = ranked.filter { it.document.id !in selectedIds }.take(maxOf(0, remaining))
                (selectedUserFacts + selectedEpisodes + filler).take(totalLimit)
            }
        }
    }

    suspend fun listByKindAndDay(
        kind: MemoryDocumentKind,
        scheduledDayKey: String,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getActiveByKindAndScheduledDay(kind.name, scheduledDayKey)
    }

    suspend fun listUpcomingEpisodesForDay(scheduledDayKey: String): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getActiveByKindAndScheduledDay(MemoryDocumentKind.EPISODE.name, scheduledDayKey)
            .filter { doc ->
                val episodeKind = runCatching { EpisodeKind.valueOf(doc.category.orEmpty()) }.getOrNull()
                episodeKind != null && episodeKind != EpisodeKind.PHYSICAL_NOW
            }
    }

    suspend fun listActiveListItems(
        type: ListItemType,
        limit: Int = 20,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getActiveByKind(MemoryDocumentKind.LIST_ITEM.name, limit * 3)
            .filter { it.category.equals(type.name, ignoreCase = true) }
            .take(limit)
    }

    suspend fun listRecentPhysicalEpisodes(
        daysBack: Int = 2,
        limit: Int = 8,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        val calendar = java.util.Calendar.getInstance(Locale.ITALY)
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val sinceDayKey = dayKeyFor(calendar.timeInMillis)
        return dao.getActiveByKind(MemoryDocumentKind.EPISODE.name, limit * 4)
            .filter {
                it.category == EpisodeKind.PHYSICAL_NOW.name &&
                    (it.dayKey ?: "") >= sinceDayKey
            }
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    suspend fun getHabitSummaryDocument(): MemoryDocumentEntity? {
        ensureMigrated()
        return dao.getByExternalRef(HABIT_SUMMARY_EXTERNAL_REF)?.takeIf { it.isActive }
            ?: dao.getActiveByKind(MemoryDocumentKind.HABIT_SUMMARY.name, 1).firstOrNull()
    }

    suspend fun saveHabitSummaryProjection(
        summaryText: String,
        sourceEventCount: Int,
        source: MemoryDocumentSource = MemoryDocumentSource.EXTRACTOR,
    ): Long {
        ensureMigrated()
        val normalized = summaryText.trim()
        if (normalized.isBlank()) return -1L
        val now = System.currentTimeMillis()
        return upsertByExternalRef(
            externalRef = HABIT_SUMMARY_EXTERNAL_REF,
            value = normalized,
            kind = MemoryDocumentKind.HABIT_SUMMARY,
            category = sourceEventCount.toString(),
            source = source,
            confidence = 1f,
            createdAt = now,
            updatedAt = now,
            expiresAt = now + EPISODE_RETENTION_MS,
        )
    }

    suspend fun getCurrentPlaceDocument(): MemoryDocumentEntity? {
        ensureMigrated()
        return dao.getByExternalRef(SPATIAL_CURRENT_EXTERNAL_REF)?.takeIf { it.isActive }
    }

    suspend fun listSpatialPlaceDocuments(limit: Int = 20): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getActiveByKind(MemoryDocumentKind.SPATIAL.name, limit * 2)
            .filter {
                it.externalRef?.startsWith("spatial_place:") == true
            }
            .take(limit)
    }

    suspend fun getVisionCatalog(limit: Int = 18): List<MemoryDocumentEntity> {
        ensureMigrated()
        val facts = dao.getActiveByKind(MemoryDocumentKind.USER_FACT.name, limit)
        return facts
            .filter { it.category in setOf(MemoryCategory.FACT.name, MemoryCategory.ROUTINE.name) }
            .sortedWith(
                compareByDescending<MemoryDocumentEntity> { it.confidence }
                    .thenByDescending { it.updatedAt },
            )
            .distinctBy { it.value.trim().lowercase() }
            .take(limit)
    }

    suspend fun getCoreIdentity(limit: Int = 2): List<MemoryDocumentEntity> {
        ensureMigrated()
        return dao.getActiveByKind(MemoryDocumentKind.USER_FACT.name, limit * 4)
            .filter { it.category == MemoryCategory.IDENTITY.name }
            .take(limit)
    }

    suspend fun getByCategories(
        categories: List<MemoryCategory>,
        limitPerCategory: Int,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        val merged = linkedMapOf<Long, MemoryDocumentEntity>()
        val names = categories.map { it.name }.toSet()
        dao.getActiveByKind(MemoryDocumentKind.USER_FACT.name, limit = 500)
            .filter { it.category in names }
            .groupBy { it.category }
            .forEach { (_, items) ->
                items.take(limitPerCategory).forEach { merged.putIfAbsent(it.id, it) }
            }
        if (categories.any { MemoryCategory.isRobotInternal(it) }) {
            dao.getActiveByKind(MemoryDocumentKind.AUTONOMY.name, limit = 100)
                .filter { it.category in names }
                .forEach { merged.putIfAbsent(it.id, it) }
        }
        return merged.values.toList()
    }

    suspend fun markUsed(documents: List<MemoryDocumentEntity>) {
        if (documents.isEmpty()) return
        dao.markUsed(documents.map { it.id }, System.currentTimeMillis())
    }

    suspend fun getUserFacingActiveDocuments(): List<MemoryDocumentEntity> {
        ensureMigrated()
        val categories = MemoryCategory.USER_FACING.map { it.name }.toSet()
        return dao.getAllActive()
            .filter { doc ->
                doc.kind == MemoryDocumentKind.USER_FACT.name &&
                    doc.category in categories
            }
            .sortedByDescending { it.updatedAt }
    }

    fun computeUserFacingContentHash(items: List<MemoryDocumentEntity>): String {
        if (items.isEmpty()) return ""
        val canonical = items
            .sortedBy { it.id }
            .joinToString("\n") { doc ->
                "${doc.category.orEmpty()}|${doc.value.trim().lowercase()}"
            }
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(canonical.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    suspend fun computeUserFacingContentHash(): String =
        computeUserFacingContentHash(getUserFacingActiveDocuments())

    /**
     * Applies consolidated lines in-place: unchanged rows keep id and useCount; only merged
     * duplicates are deactivated. New rows are created only when no input match exists.
     */
    suspend fun replaceUserFacingWithConsolidated(lines: List<ConsolidatedMemoryLine>): Int {
        ensureMigrated()
        if (lines.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val active = getUserFacingActiveDocuments()
        val plan = MemoryConsolidationApplicator.plan(
            active = active.map { it.toConsolidationRow() },
            consolidated = lines,
        )

        plan.deactivateIds.forEach { dao.deactivateById(it, now) }
        plan.updates.forEach { (id, update) ->
            val existing = dao.getById(id) ?: return@forEach
            updateDocument(
                existing.copy(
                    value = update.value,
                    category = update.category.name,
                    confidence = MemorySafetyPinDetector.applyConfidenceFloor(
                        confidence = existing.confidence,
                        value = update.value,
                        category = update.category,
                    ),
                    useCount = update.useCount,
                    lastUsedAt = update.lastUsedAt,
                    updatedAt = now,
                    source = MemoryDocumentSource.CONSOLIDATION.name,
                ),
            )
        }
        plan.inserts.forEach { line ->
            upsertUserFacingFact(
                category = line.category,
                value = line.value,
                confidence = 1f,
                source = MemoryDocumentSource.CONSOLIDATION,
            )
        }
        return getUserFacingActiveDocuments().size
    }

    private fun MemoryDocumentEntity.toConsolidationRow(): MemoryConsolidationApplicator.MemoryRow {
        val cat = runCatching { MemoryCategory.valueOf(category.orEmpty()) }.getOrNull()
            ?: MemoryCategory.FACT
        return MemoryConsolidationApplicator.MemoryRow(
            id = id,
            category = cat,
            value = value,
            useCount = useCount,
            lastUsedAt = lastUsedAt,
            updatedAt = updatedAt,
            createdAt = createdAt,
        )
    }

    suspend fun countActive(): Int = dao.countActive()

    suspend fun upsertUserFacingFact(
        category: MemoryCategory,
        value: String,
        confidence: Float,
        source: MemoryDocumentSource,
        sourceMessageId: Long = 0L,
    ): Long {
        require(MemoryCategory.isUserFacing(category)) {
            "Use upsertAutonomy for robot-internal categories"
        }
        val normalized = value.trim()
        if (normalized.isBlank()) return -1L
        val effectiveConfidence = MemorySafetyPinDetector.applyConfidenceFloor(
            confidence = confidence,
            value = normalized,
            category = category,
        )
        val now = System.currentTimeMillis()
        val existing = findUserFacingDuplicate(category, normalized)
        val entity = if (existing != null) {
            existing.copy(
                value = normalized,
                confidence = maxOf(existing.confidence, effectiveConfidence),
                updatedAt = now,
                isActive = true,
                expiresAt = null,
            )
        } else {
            MemoryDocumentEntity(
                value = normalized,
                kind = MemoryDocumentKind.USER_FACT.name,
                category = category.name,
                source = source.name,
                confidence = effectiveConfidence,
                createdAt = now,
                updatedAt = now,
            )
        }
        return upsertDocument(entity)
    }

    suspend fun upsertAutonomy(
        category: MemoryCategory,
        value: String,
        confidence: Float = 0.85f,
        source: MemoryDocumentSource = MemoryDocumentSource.TOOL,
        ttlDays: Int? = null,
    ): AutonomyUpsertResult {
        require(MemoryCategory.isRobotInternal(category)) {
            "upsertAutonomy is only for OBSERVATION, INTENT, PATTERN"
        }
        val normalized = value.trim()
        if (normalized.isBlank()) return AutonomyUpsertResult.InvalidValue

        if (category == MemoryCategory.INTENT && countActiveIntents() >= UserMemoryRepository.MAX_ACTIVE_INTENTS) {
            val existing = findAutonomyExact(MemoryCategory.INTENT, normalized)
            if (existing == null) return AutonomyUpsertResult.IntentCapReached
        }

        val now = System.currentTimeMillis()
        val effectiveTtlDays = ttlDays ?: defaultAutonomyTtlDays(category)
        val expiresAt = now + TimeUnit.DAYS.toMillis(effectiveTtlDays.toLong())
        val existing = when (category) {
            MemoryCategory.INTENT -> findAutonomyExact(MemoryCategory.INTENT, normalized)
            MemoryCategory.OBSERVATION -> findObservationDuplicate(normalized)
            else -> findAutonomyExact(category, normalized)
        }
        val entity = if (existing != null) {
            existing.copy(
                value = normalized,
                confidence = maxOf(existing.confidence, confidence.coerceIn(0f, 1f)),
                updatedAt = now,
                expiresAt = expiresAt,
                isActive = true,
            )
        } else {
            MemoryDocumentEntity(
                value = normalized,
                kind = MemoryDocumentKind.AUTONOMY.name,
                category = category.name,
                source = source.name,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = now,
                updatedAt = now,
                expiresAt = expiresAt,
            )
        }
        return AutonomyUpsertResult.Success(upsertDocument(entity))
    }

    suspend fun deleteById(id: Long): Boolean {
        if (id <= 0L) return false
        val existing = dao.getById(id) ?: return false
        if (!existing.isActive) return false
        dao.deactivateById(id)
        return true
    }

    suspend fun forgetByTopic(query: String): ForgetByTopicResult {
        val q = query.trim()
        if (q.isBlank()) return ForgetByTopicResult(0, emptyList(), emptyList())
        val active = dao.getAllActive().filter { doc ->
            doc.kind == MemoryDocumentKind.USER_FACT.name ||
                doc.kind == MemoryDocumentKind.AUTONOMY.name
        }
        var matches = MemoryTopicMatcher.rank(q, active.map { it.asTopicMatchEntity() })
            .filter { it.score >= MemoryTopicMatcher.MIN_FORGET_SCORE }
        if (matches.isEmpty()) {
            matches = MemoryTopicMatcher.fallbackMatches(q, active.map { it.asTopicMatchEntity() })
                .filter { it.score >= 0.34f }
        }
        if (matches.isEmpty()) return ForgetByTopicResult(0, emptyList(), emptyList())
        val now = System.currentTimeMillis()
        val values = mutableListOf<String>()
        val ids = mutableListOf<Long>()
        matches.forEach { scored ->
            dao.deactivateById(scored.item.id, now)
            values += scored.item.value
            ids += scored.item.id
        }
        return ForgetByTopicResult(values.size, values, ids)
    }

    suspend fun updateValue(id: Long, value: String): Boolean {
        val normalized = value.trim()
        if (id <= 0L || normalized.isBlank()) return false
        val existing = dao.getById(id) ?: return false
        if (!existing.isActive) return false
        updateDocument(
            existing.copy(
                value = normalized,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun resetUserFacingMemory() {
        ensureMigrated()
        val now = System.currentTimeMillis()
        val categories = MemoryCategory.USER_FACING.map { it.name }.toSet()
        dao.getAllActive(now)
            .filter { doc ->
                doc.kind == MemoryDocumentKind.USER_FACT.name && doc.category in categories
            }
            .forEach { doc -> dao.deactivateById(doc.id, now) }
    }

    suspend fun reorganize(): Int {
        ensureMigrated()
        val active = getUserFacingActiveDocuments()
        val toDelete = mutableSetOf<Long>()
        val now = System.currentTimeMillis()
        active.groupBy { it.category }.values.forEach { categoryItems ->
            val remaining = categoryItems.filter { it.id !in toDelete }
            for (i in remaining.indices) {
                val anchor = remaining[i]
                if (anchor.id in toDelete) continue
                val category = runCatching {
                    MemoryCategory.valueOf(anchor.category.orEmpty())
                }.getOrNull() ?: continue
                val cluster = mutableListOf(anchor)
                for (j in i + 1 until remaining.size) {
                    val other = remaining[j]
                    if (other.id in toDelete) continue
                    if (MemoryDuplicateDetector.areDuplicates(anchor.value, other.value, category)) {
                        cluster += other
                    }
                }
                if (cluster.size <= 1) continue
                val keeper = cluster.maxWithOrNull(
                    compareBy<MemoryDocumentEntity> { it.useCount }
                        .thenBy { it.confidence }
                        .thenBy { it.updatedAt },
                ) ?: continue
                val mergedUseCount = cluster.sumOf { it.useCount }
                val mergedLastUsed = cluster.maxOf { it.lastUsedAt }
                if (mergedUseCount != keeper.useCount || mergedLastUsed != keeper.lastUsedAt) {
                    dao.update(
                        keeper.copy(
                            useCount = mergedUseCount,
                            lastUsedAt = mergedLastUsed,
                            updatedAt = now,
                        ),
                    )
                }
                cluster.filter { it.id != keeper.id }.forEach { toDelete += it.id }
            }
        }
        toDelete.forEach { dao.deactivateById(it, now) }
        return toDelete.size
    }

    suspend fun pruneIfNeeded(maxItems: Int): Int {
        pruneExpiredAutonomy()
        val userFacts = dao.getAllActive()
            .filter { it.kind == MemoryDocumentKind.USER_FACT.name }
        val prunable = userFacts.filterNot { isSafetyPinnedDocument(it) }
        if (prunable.size <= maxItems) return 0
        val toDelete = prunable.size - maxItems
        val lowPriority = prunable
            .sortedWith(
                compareBy<MemoryDocumentEntity> { it.confidence }
                    .thenBy { it.useCount }
                    .thenBy { it.lastUsedAt }
                    .thenBy { it.updatedAt },
            )
            .take(toDelete)
        val now = System.currentTimeMillis()
        lowPriority.forEach { dao.deactivateById(it.id, now) }
        return lowPriority.size
    }

    private fun isSafetyPinnedDocument(document: MemoryDocumentEntity): Boolean {
        val category = runCatching {
            MemoryCategory.valueOf(document.category.orEmpty())
        }.getOrNull() ?: return false
        return MemorySafetyPinDetector.isSafetyPinned(document.value, category)
    }

    suspend fun searchToolRelevant(
        query: String,
        limit: Int,
        includeRobotInternal: Boolean = false,
    ): List<MemoryDocumentEntity> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        if (textEmbedder.isAvailable) {
            val hybrid = searchRelevant(q, limit = limit)
                .filter { doc ->
                    doc.kind == MemoryDocumentKind.USER_FACT.name ||
                        (includeRobotInternal && doc.kind == MemoryDocumentKind.AUTONOMY.name)
                }
            if (hybrid.isNotEmpty()) return hybrid
        }
        val active = dao.getAllActive().filter { doc ->
            when (doc.kind) {
                MemoryDocumentKind.USER_FACT.name -> true
                MemoryDocumentKind.AUTONOMY.name -> includeRobotInternal
                else -> false
            }
        }
        val ranked = MemoryTopicMatcher.rank(q, active.map { it.asTopicMatchEntity() }, limit)
        if (ranked.isNotEmpty()) {
            return ranked.map { scored ->
                active.first { it.id == scored.item.id }
            }
        }
        val fallback = MemoryTopicMatcher.fallbackMatches(q, active.map { it.asTopicMatchEntity() })
            .take(limit)
        if (fallback.isNotEmpty()) {
            return fallback.map { scored -> active.first { it.id == scored.item.id } }
        }
        return searchRelevant(q, limit = limit, minScore = MemorySearchScorer.DEFAULT_MIN_SCORE)
            .filter { doc ->
                doc.kind == MemoryDocumentKind.USER_FACT.name ||
                    (includeRobotInternal && doc.kind == MemoryDocumentKind.AUTONOMY.name)
            }
    }

    suspend fun getToolByCategory(
        category: MemoryCategory,
        limit: Int,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        val kind = if (MemoryCategory.isRobotInternal(category)) {
            MemoryDocumentKind.AUTONOMY
        } else {
            MemoryDocumentKind.USER_FACT
        }
        return dao.getActiveByKind(kind.name, limit * 2)
            .filter { it.category.equals(category.name, ignoreCase = true) }
            .take(limit)
    }

    suspend fun getRecentObservations(
        limit: Int = UserMemoryRepository.DEFAULT_OBSERVATION_LIMIT,
        maxAgeDays: Int = UserMemoryRepository.DEFAULT_OBSERVATION_TTL_DAYS,
    ): List<MemoryDocumentEntity> {
        ensureMigrated()
        pruneExpiredAutonomy()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(maxAgeDays.toLong())
        return dao.getActiveByKind(MemoryDocumentKind.AUTONOMY.name, limit.coerceAtLeast(1) * 2)
            .filter {
                it.category == MemoryCategory.OBSERVATION.name && it.updatedAt >= cutoff
            }
            .take(limit.coerceAtLeast(1))
    }

    suspend fun searchRelevantExpanded(
        query: String,
        limit: Int,
        includeRobotInternal: Boolean = false,
    ): List<MemoryDocumentEntity> {
        val expanded = expandQueryForSearch(query)
        val merged = linkedMapOf<Long, MemoryDocumentEntity>()
        for (q in expanded) {
            searchToolRelevant(q, limit, includeRobotInternal).forEach { doc ->
                merged.putIfAbsent(doc.id, doc)
            }
            if (merged.size >= limit) break
        }
        return merged.values.take(limit).toList()
    }

    suspend fun pruneExpired() {
        pruneExpiredAutonomy()
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

    private suspend fun countActiveIntents(): Int =
        dao.getActiveByKind(MemoryDocumentKind.AUTONOMY.name, UserMemoryRepository.MAX_ACTIVE_INTENTS * 2)
            .count { it.category == MemoryCategory.INTENT.name }

    private suspend fun pruneExpiredAutonomy() {
        val now = System.currentTimeMillis()
        dao.getAllActive(now)
            .filter { doc ->
                doc.kind == MemoryDocumentKind.AUTONOMY.name &&
                    doc.expiresAt != null &&
                    doc.expiresAt <= now
            }
            .forEach { doc -> dao.deactivateById(doc.id, now) }
    }

    private suspend fun findUserFacingDuplicate(
        category: MemoryCategory,
        value: String,
    ): MemoryDocumentEntity? =
        getUserFacingActiveDocuments()
            .filter { it.category == category.name }
            .filter { MemoryDuplicateDetector.areDuplicates(value, it.value, category) }
            .maxWithOrNull(
                compareBy<MemoryDocumentEntity> { it.confidence }
                    .thenBy { it.updatedAt },
            )

    private suspend fun findAutonomyExact(
        category: MemoryCategory,
        value: String,
    ): MemoryDocumentEntity? =
        dao.getActiveByKind(MemoryDocumentKind.AUTONOMY.name, 100)
            .firstOrNull {
                it.category == category.name &&
                    it.value.equals(value, ignoreCase = true)
            }

    private suspend fun findObservationDuplicate(value: String): MemoryDocumentEntity? {
        val dateKey = extractDateKey(value) ?: return null
        return dao.getActiveByKind(MemoryDocumentKind.AUTONOMY.name, 50)
            .filter { it.category == MemoryCategory.OBSERVATION.name }
            .firstOrNull { extractDateKey(it.value) == dateKey }
    }

    private fun extractDateKey(value: String): String? {
        val match = DATE_KEY_REGEX.find(value.lowercase()) ?: return null
        return match.value
    }

    private fun defaultAutonomyTtlDays(category: MemoryCategory): Int = when (category) {
        MemoryCategory.OBSERVATION -> UserMemoryRepository.DEFAULT_OBSERVATION_TTL_DAYS
        MemoryCategory.INTENT -> UserMemoryRepository.DEFAULT_INTENT_TTL_DAYS
        MemoryCategory.PATTERN -> UserMemoryRepository.DEFAULT_PATTERN_TTL_DAYS
        else -> UserMemoryRepository.DEFAULT_OBSERVATION_TTL_DAYS
    }

    private fun MemoryDocumentEntity.asTopicMatchEntity(): MemoryItemEntity {
        val cat = runCatching { MemoryCategory.valueOf(category.orEmpty()) }.getOrNull()
            ?: MemoryCategory.FACT
        return MemoryItemEntity(
            id = id,
            category = cat,
            value = value,
            confidence = confidence,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastUsedAt = lastUsedAt,
            useCount = useCount,
            sourceMessageId = 0L,
            expiresAt = expiresAt,
        )
    }

    private suspend fun upsertByExternalRef(
        externalRef: String,
        value: String,
        kind: MemoryDocumentKind,
        category: String?,
        source: MemoryDocumentSource,
        confidence: Float = 1f,
        createdAt: Long,
        updatedAt: Long,
        expiresAt: Long? = null,
        isActive: Boolean = true,
        dayKey: String? = null,
        scheduledDayKey: String? = null,
        scheduledAtMs: Long? = null,
        actor: String? = null,
        sourceChannel: String? = null,
        episodeConfidence: String? = null,
        isUnread: Boolean = false,
        linkedActivityLogId: Long? = null,
    ): Long {
        val existing = dao.getByExternalRef(externalRef)
        val entity = if (existing != null) {
            existing.copy(
                value = value,
                kind = kind.name,
                category = category,
                source = source.name,
                confidence = maxOf(existing.confidence, confidence.coerceIn(0f, 1f)),
                updatedAt = updatedAt,
                expiresAt = expiresAt ?: existing.expiresAt,
                isActive = isActive,
                dayKey = dayKey ?: existing.dayKey,
                scheduledDayKey = scheduledDayKey ?: existing.scheduledDayKey,
                scheduledAtMs = scheduledAtMs ?: existing.scheduledAtMs,
                actor = actor ?: existing.actor,
                sourceChannel = sourceChannel ?: existing.sourceChannel,
                episodeConfidence = episodeConfidence ?: existing.episodeConfidence,
                isUnread = isUnread || existing.isUnread,
                linkedActivityLogId = linkedActivityLogId ?: existing.linkedActivityLogId,
            )
        } else {
            MemoryDocumentEntity(
                value = value,
                kind = kind.name,
                category = category,
                source = source.name,
                confidence = confidence.coerceIn(0f, 1f),
                createdAt = createdAt,
                updatedAt = updatedAt,
                expiresAt = expiresAt,
                isActive = isActive,
                dayKey = dayKey,
                scheduledDayKey = scheduledDayKey,
                scheduledAtMs = scheduledAtMs,
                actor = actor,
                sourceChannel = sourceChannel,
                episodeConfidence = episodeConfidence,
                externalRef = externalRef,
                isUnread = isUnread,
                linkedActivityLogId = linkedActivityLogId,
            )
        }
        return upsertDocument(entity)
    }

    private suspend fun loadCandidates(
        filters: MemoryDocumentFilters,
        now: Long,
    ): List<MemoryDocumentEntity> {
        val all = dao.getAllActive(now)
        return all.filter { doc ->
            if (filters.activeOnly && !doc.isActive) return@filter false
            val kind = runCatching { MemoryDocumentKind.valueOf(doc.kind) }.getOrNull()
            if (filters.kinds != null && kind !in filters.kinds) return@filter false
            if (filters.categories != null && doc.category !in filters.categories) return@filter false
            if (filters.scheduledDayKey != null && doc.scheduledDayKey != filters.scheduledDayKey) {
                return@filter false
            }
            if (filters.dayKey != null) {
                val matchesDay = doc.dayKey == filters.dayKey || doc.scheduledDayKey == filters.dayKey
                if (!matchesDay) return@filter false
            }
            if (filters.actor != null) {
                val actor = doc.actor?.trim()?.lowercase().orEmpty()
                if (actor.isBlank() || !actor.contains(filters.actor.trim().lowercase())) {
                    return@filter false
                }
            }
            true
        }
    }

    companion object {
        internal val EPISODE_RETENTION_MS =
            TimeUnit.DAYS.toMillis(ActivityLogRepository.RETENTION_DAYS.toLong())

        fun reminderExternalRef(taskId: Long): String = "reminder:$taskId"

        fun listItemExternalRef(itemId: Long): String = "list_item:$itemId"

        fun activityLogExternalRef(eventId: Long): String = "activity_log:$eventId"

        fun notificationExternalRef(dedupKey: String): String = "$NOTIFICATION_REF_PREFIX$dedupKey"

        private const val NOTIFICATION_REF_PREFIX = "notification:"

        fun spatialPlaceExternalRef(placeId: Long): String = "spatial_place:$placeId"

        const val SPATIAL_CURRENT_EXTERNAL_REF: String = "spatial:current"

        const val HABIT_SUMMARY_EXTERNAL_REF: String = "habit_summary:current"

        private val EMBEDDABLE_KINDS = setOf(
            MemoryDocumentKind.USER_FACT.name,
            MemoryDocumentKind.AUTONOMY.name,
            MemoryDocumentKind.EPISODE.name,
            MemoryDocumentKind.REMINDER.name,
            MemoryDocumentKind.LIST_ITEM.name,
            MemoryDocumentKind.HABIT_SUMMARY.name,
        )

        private val DATE_KEY_REGEX = Regex("""\d{1,2}\s+\w+\s+\d{4}""")

        fun dayKeyFor(timestampMs: Long): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
            return format.format(timestampMs)
        }

        fun create(
            context: Context,
            userMemoryRepository: UserMemoryRepository,
            scheduledTaskRepository: ScheduledTaskRepository,
            settingsRepository: MemorySettingsRepository,
            listItemRepository: ListItemRepository,
            activityLogRepository: ActivityLogRepository,
            spatialPlaceRepository: SpatialPlaceRepository?,
            spatialContextRepository: SpatialContextRepository?,
        ): UnifiedMemoryRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                MemoryDocumentDatabase::class.java,
                "memory_documents.db",
            )
                .addMigrations(MemoryDocumentDatabase.MIGRATION_1_2)
                .build()
            val dao = db.memoryDocumentDao()
            val migration = MemoryDocumentMigration(
                dao = dao,
                userMemoryRepository = userMemoryRepository,
                scheduledTaskRepository = scheduledTaskRepository,
                settingsRepository = settingsRepository,
                listItemRepository = listItemRepository,
                activityLogRepository = activityLogRepository,
                spatialPlaceRepository = spatialPlaceRepository,
                spatialContextRepository = spatialContextRepository,
            )
            return UnifiedMemoryRepository(
                dao = dao,
                migrationRunner = { migration.runIfNeeded() },
                textEmbedderProvider = {
                    com.example.mydeskrobot.memory.unified.embedding.EmbeddingRuntime.getEmbedder(
                        context.applicationContext,
                    )
                },
            )
        }

        fun createForTest(
            dao: MemoryDocumentDao,
            migrationRunner: suspend () -> Unit = {},
            textEmbedderProvider: () -> TextEmbedder = { NoOpTextEmbedder },
        ): UnifiedMemoryRepository = UnifiedMemoryRepository(
            dao = dao,
            migrationRunner = migrationRunner,
            textEmbedderProvider = textEmbedderProvider,
        )
    }
}

internal class MemoryDocumentMigration(
    private val dao: MemoryDocumentDao,
    private val userMemoryRepository: UserMemoryRepository,
    private val scheduledTaskRepository: ScheduledTaskRepository,
    private val settingsRepository: MemorySettingsRepository,
    private val listItemRepository: ListItemRepository,
    private val activityLogRepository: ActivityLogRepository,
    private val spatialPlaceRepository: SpatialPlaceRepository?,
    private val spatialContextRepository: SpatialContextRepository?,
) {
    suspend fun runIfNeeded() {
        if (!settingsRepository.isUnifiedMemoryMigrated()) {
            migrateUserMemories()
            migratePendingReminders()
            settingsRepository.setUnifiedMemoryMigrated(true)
        }
        if (!settingsRepository.isUnifiedProjectionsMigrated()) {
            migrateListItems()
            migrateActivityLog()
            migrateSpatialPlaces()
            migrateCurrentPlace()
            settingsRepository.setUnifiedProjectionsMigrated(true)
        }
        migrateHabitSummaryIfMissing()
    }

    private suspend fun migrateUserMemories() {
        val active = userMemoryRepository.getAllActive()
        active.forEach { item ->
            val kind = if (MemoryCategory.isRobotInternal(item.category)) {
                MemoryDocumentKind.AUTONOMY
            } else {
                MemoryDocumentKind.USER_FACT
            }
            dao.upsert(
                MemoryDocumentEntity(
                    value = item.value,
                    kind = kind.name,
                    category = item.category.name,
                    source = MemoryDocumentSource.MIGRATION.name,
                    confidence = item.confidence,
                    useCount = item.useCount,
                    lastUsedAt = item.lastUsedAt,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt,
                    expiresAt = item.expiresAt,
                    externalRef = "legacy_memory:${item.id}",
                ),
            )
        }
        if (active.isNotEmpty()) {
            // touch to ensure migration timestamp is fresh
            dao.countActive()
        }
    }

    private suspend fun migratePendingReminders() {
        scheduledTaskRepository.listPending().forEach { task ->
            migrateReminder(task)
        }
    }

    private suspend fun migrateReminder(task: ScheduledTaskEntity) {
        val now = System.currentTimeMillis()
        dao.upsert(
            MemoryDocumentEntity(
                value = task.message,
                kind = MemoryDocumentKind.REMINDER.name,
                category = null,
                source = MemoryDocumentSource.MIGRATION.name,
                confidence = 1f,
                createdAt = task.createdAtMillis,
                updatedAt = now,
                scheduledDayKey = UnifiedMemoryRepository.dayKeyFor(task.triggerAtMillis),
                scheduledAtMs = task.triggerAtMillis,
                externalRef = UnifiedMemoryRepository.reminderExternalRef(task.id),
            ),
        )
    }

    private suspend fun migrateListItems() {
        listItemRepository.list(limit = ListItemRepository.MAX_LIMIT).forEach { item ->
            migrateListItem(item)
        }
    }

    private suspend fun migrateListItem(item: ListItemEntity) {
        val active = item.type == ListItemType.NOTE || !item.checked
        dao.upsert(
            MemoryDocumentEntity(
                value = item.text,
                kind = MemoryDocumentKind.LIST_ITEM.name,
                category = item.type.name,
                source = MemoryDocumentSource.MIGRATION.name,
                confidence = 1f,
                createdAt = item.createdAtMillis,
                updatedAt = item.updatedAtMillis,
                isActive = active,
                externalRef = UnifiedMemoryRepository.listItemExternalRef(item.id),
            ),
        )
    }

    private suspend fun migrateActivityLog() {
        val sinceMs = System.currentTimeMillis() - UnifiedMemoryRepository.EPISODE_RETENTION_MS
        activityLogRepository.getEventsGroupedByDay()
            .flatMap { it.events }
            .filter { it.timestampMs >= sinceMs }
            .forEach { event ->
                migrateActivityEvent(event.id, event)
            }
    }

    private suspend fun migrateActivityEvent(
        eventId: Long,
        event: com.example.mydeskrobot.domain.activitylog.ActivityLogEntry,
    ) {
        val expiresAt = event.timestampMs + UnifiedMemoryRepository.EPISODE_RETENTION_MS
        dao.upsert(
            MemoryDocumentEntity(
                value = EpisodeProjectionValue.format(event.label, event.rawPhrase),
                kind = MemoryDocumentKind.EPISODE.name,
                category = event.eventKind.name,
                source = MemoryDocumentSource.MIGRATION.name,
                confidence = if (event.confidence == EpisodeConfidence.CONFIRMED) 1f else 0.6f,
                createdAt = event.timestampMs,
                updatedAt = event.timestampMs,
                expiresAt = expiresAt,
                dayKey = event.dayKey,
                scheduledDayKey = event.scheduledDayKey ?: event.dayKey,
                scheduledAtMs = event.scheduledAtMs,
                actor = event.actor,
                sourceChannel = event.sourceChannel,
                episodeConfidence = event.confidence.name,
                externalRef = UnifiedMemoryRepository.activityLogExternalRef(eventId),
            ),
        )
    }

    private suspend fun migrateSpatialPlaces() {
        val repository = spatialPlaceRepository ?: return
        repository.listActive().forEach { place ->
            migrateSpatialPlace(place)
        }
    }

    private suspend fun migrateSpatialPlace(place: SpatialPlace) {
        val landmarkText = place.landmarks.joinToString(", ")
        val value = buildString {
            append(place.label)
            if (landmarkText.isNotBlank()) append(": ").append(landmarkText)
            if (place.description.isNotBlank()) append(" — ").append(place.description)
        }
        dao.upsert(
            MemoryDocumentEntity(
                value = value,
                kind = MemoryDocumentKind.SPATIAL.name,
                category = place.roomType.name.lowercase(),
                source = MemoryDocumentSource.MIGRATION.name,
                confidence = 1f,
                createdAt = place.createdAt,
                updatedAt = place.updatedAt,
                externalRef = UnifiedMemoryRepository.spatialPlaceExternalRef(place.id),
            ),
        )
    }

    private suspend fun migrateCurrentPlace() {
        val snapshot = spatialContextRepository?.load() ?: return
        val label = snapshot.currentPlaceLabel ?: return
        dao.upsert(
            MemoryDocumentEntity(
                value = "Stanza corrente: $label",
                kind = MemoryDocumentKind.SPATIAL.name,
                category = snapshot.roomType?.name?.lowercase() ?: "current",
                source = MemoryDocumentSource.MIGRATION.name,
                confidence = snapshot.confidence.coerceIn(0f, 1f),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                externalRef = UnifiedMemoryRepository.SPATIAL_CURRENT_EXTERNAL_REF,
            ),
        )
    }

    private suspend fun migrateHabitSummaryIfMissing() {
        if (dao.getByExternalRef(UnifiedMemoryRepository.HABIT_SUMMARY_EXTERNAL_REF) != null) return
        val profile = activityLogRepository.getHabitSummary() ?: return
        val summary = profile.summaryText.trim()
        if (summary.isBlank()) return
        val now = System.currentTimeMillis()
        dao.upsert(
            MemoryDocumentEntity(
                value = summary,
                kind = MemoryDocumentKind.HABIT_SUMMARY.name,
                category = profile.sourceEventCount.toString(),
                source = MemoryDocumentSource.MIGRATION.name,
                confidence = 1f,
                createdAt = profile.updatedAtMs,
                updatedAt = profile.updatedAtMs,
                expiresAt = now + UnifiedMemoryRepository.EPISODE_RETENTION_MS,
                externalRef = UnifiedMemoryRepository.HABIT_SUMMARY_EXTERNAL_REF,
            ),
        )
    }
}
