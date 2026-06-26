package com.example.mydeskrobot.memory.unified.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_documents",
    indices = [
        Index(value = ["kind"]),
        Index(value = ["category"]),
        Index(value = ["isActive"]),
        Index(value = ["scheduledDayKey"]),
        Index(value = ["externalRef"]),
        Index(value = ["useCount"]),
        Index(value = ["lastUsedAt"]),
        Index(value = ["expiresAt"]),
    ],
)
data class MemoryDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val value: String,
    val kind: String,
    val category: String?,
    val source: String,
    val embedding: ByteArray? = null,
    val confidence: Float,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long? = null,
    val isActive: Boolean = true,
    val dayKey: String? = null,
    val scheduledDayKey: String? = null,
    val scheduledAtMs: Long? = null,
    val actor: String? = null,
    val sourceChannel: String? = null,
    val episodeConfidence: String? = null,
    val externalRef: String? = null,
    val isUnread: Boolean = false,
    val linkedActivityLogId: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoryDocumentEntity) return false
        return id == other.id &&
            value == other.value &&
            kind == other.kind &&
            category == other.category &&
            source == other.source &&
            embedding.contentEquals(other.embedding) &&
            confidence == other.confidence &&
            useCount == other.useCount &&
            lastUsedAt == other.lastUsedAt &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            expiresAt == other.expiresAt &&
            isActive == other.isActive &&
            dayKey == other.dayKey &&
            scheduledDayKey == other.scheduledDayKey &&
            scheduledAtMs == other.scheduledAtMs &&
            actor == other.actor &&
            sourceChannel == other.sourceChannel &&
            episodeConfidence == other.episodeConfidence &&
            externalRef == other.externalRef &&
            isUnread == other.isUnread &&
            linkedActivityLogId == other.linkedActivityLogId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + confidence.hashCode()
        result = 31 * result + useCount
        result = 31 * result + lastUsedAt.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (expiresAt?.hashCode() ?: 0)
        result = 31 * result + isActive.hashCode()
        result = 31 * result + (dayKey?.hashCode() ?: 0)
        result = 31 * result + (scheduledDayKey?.hashCode() ?: 0)
        result = 31 * result + (scheduledAtMs?.hashCode() ?: 0)
        result = 31 * result + (actor?.hashCode() ?: 0)
        result = 31 * result + (sourceChannel?.hashCode() ?: 0)
        result = 31 * result + (episodeConfidence?.hashCode() ?: 0)
        result = 31 * result + (externalRef?.hashCode() ?: 0)
        result = 31 * result + isUnread.hashCode()
        result = 31 * result + (linkedActivityLogId?.hashCode() ?: 0)
        return result
    }
}
