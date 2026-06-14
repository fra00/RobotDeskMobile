package com.example.mydeskrobot.data.check

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.check.db.FireAndCheckDatabase
import com.example.mydeskrobot.data.check.db.FireAndCheckEntity
import com.example.mydeskrobot.domain.check.FireAndCheckEntry
import com.example.mydeskrobot.domain.check.FireAndCheckPhase
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive

class FireAndCheckRepository(
    private val database: FireAndCheckDatabase,
) {
    private val dao = database.fireAndCheckDao()
    private var pendingPrimary: PendingPrimary? = null

    fun observeActive(): Flow<List<FireAndCheckEntry>> {
        val fromStore = dao.observeByStatus(FireAndCheckStatus.ACTIVE).map { list ->
            list.map { it.toEntry() }
        }
        val ticker = flow {
            while (currentCoroutineContext().isActive) {
                delay(ACTIVE_TICK_MS)
                emit(dao.listByStatus(FireAndCheckStatus.ACTIVE).map { it.toEntry() })
            }
        }
        return merge(fromStore, ticker).distinctUntilChanged()
    }

    suspend fun onPrimaryReminderScheduled(
        reminderId: Long,
        primaryMessage: String,
        primaryDueAtMillis: Long,
        checkGoal: String?,
        triggerReason: String?,
        fireAndCheck: Boolean,
    ) {
        pendingPrimary = PendingPrimary(
            reminderId = reminderId,
            message = primaryMessage,
            dueAtMillis = primaryDueAtMillis,
        )

        if (fireAndCheck || !checkGoal.isNullOrBlank() || !triggerReason.isNullOrBlank()) {
            insertPrimaryEntity(
                reminderId = reminderId,
                primaryMessage = primaryMessage,
                primaryDueAtMillis = primaryDueAtMillis,
                checkGoal = checkGoal,
                triggerReason = triggerReason,
            )
        }
    }

    suspend fun onVerificationReminderScheduled(
        verificationReminderId: Long,
        verificationMessage: String,
        verificationDueAtMillis: Long,
    ) {
        val primary = dao.findLatestAwaitingVerificationLink()
            ?: pendingPrimary?.let { pending ->
                insertPrimaryEntity(
                    reminderId = pending.reminderId,
                    primaryMessage = pending.message,
                    primaryDueAtMillis = pending.dueAtMillis,
                    checkGoal = null,
                    triggerReason = pending.message,
                )
                dao.findByPrimaryReminderId(pending.reminderId)
            }
            ?: return

        pendingPrimary = null
        val updated = primary.copy(
            verificationReminderId = verificationReminderId,
            verificationMessage = verificationMessage.trim(),
            verificationDueAtMillis = verificationDueAtMillis,
            checkGoal = primary.checkGoal ?: verificationMessage.trim(),
        )
        dao.update(updated)
    }

    private suspend fun insertPrimaryEntity(
        reminderId: Long,
        primaryMessage: String,
        primaryDueAtMillis: Long,
        checkGoal: String?,
        triggerReason: String?,
    ) {
        if (dao.findByPrimaryReminderId(reminderId) != null) return

        val reason = triggerReason?.trim().orEmpty().ifBlank { primaryMessage }
        dao.insert(
            FireAndCheckEntity(
                triggerReason = reason,
                checkGoal = checkGoal?.trim()?.takeIf { it.isNotBlank() },
                primaryMessage = primaryMessage,
                primaryReminderId = reminderId,
                verificationMessage = null,
                verificationReminderId = null,
                primaryDueAtMillis = primaryDueAtMillis,
                verificationDueAtMillis = null,
                status = FireAndCheckStatus.ACTIVE,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun onReminderFired(reminderId: Long) {
        val byPrimary = dao.findByPrimaryReminderId(reminderId)
        if (byPrimary != null) {
            dao.update(
                byPrimary.copy(
                    primaryFiredAtMillis = System.currentTimeMillis(),
                ),
            )
            return
        }

        val byVerification = dao.findByVerificationReminderId(reminderId)
        if (byVerification != null) {
            // Verification alarm fired — keep ACTIVE until LLM cycle completes.
        }
    }

    suspend fun shouldCompleteOnReminderHandled(reminderId: Long): Boolean {
        if (dao.findByVerificationReminderId(reminderId) != null) return true
        val primary = dao.findByPrimaryReminderId(reminderId) ?: return false
        return primary.verificationReminderId == null
    }

    suspend fun completeAfterVerificationHandled(reminderId: Long) {
        val entry = dao.findByVerificationReminderId(reminderId)
            ?: dao.findByPrimaryReminderId(reminderId)?.takeIf { it.verificationReminderId == null }
        if (entry != null) {
            dao.updateStatus(entry.id, FireAndCheckStatus.COMPLETED)
        }
    }

    suspend fun enrichLatestTriggerReason(userPhrase: String) {
        val phrase = userPhrase.trim()
        if (phrase.isBlank()) return
        val latest = dao.listByStatus(FireAndCheckStatus.ACTIVE).maxByOrNull { it.createdAtMillis }
            ?: return
        if (latest.triggerReason != latest.primaryMessage) return
        dao.update(latest.copy(triggerReason = phrase))
    }

    suspend fun cancelByReminderId(reminderId: Long) {
        val entry = dao.findByPrimaryReminderId(reminderId)
            ?: dao.findByVerificationReminderId(reminderId)
        if (entry != null) {
            dao.updateStatus(entry.id, FireAndCheckStatus.CANCELLED)
        }
    }

    private fun FireAndCheckEntity.toEntry(): FireAndCheckEntry {
        val now = System.currentTimeMillis()
        val phase = when {
            primaryFiredAtMillis == null &&
                primaryDueAtMillis != null &&
                now < primaryDueAtMillis -> FireAndCheckPhase.SCHEDULED
            verificationDueAtMillis != null &&
                now < verificationDueAtMillis -> FireAndCheckPhase.AWAITING_VERIFICATION
            else -> FireAndCheckPhase.CHECK_PENDING
        }
        return FireAndCheckEntry(
            id = id,
            triggerReason = triggerReason,
            checkGoal = checkGoal,
            primaryMessage = primaryMessage,
            verificationMessage = verificationMessage,
            primaryDueAtMillis = primaryDueAtMillis,
            verificationDueAtMillis = verificationDueAtMillis,
            phase = phase,
        )
    }

    private data class PendingPrimary(
        val reminderId: Long,
        val message: String,
        val dueAtMillis: Long,
    )

    companion object {
        private const val ACTIVE_TICK_MS = 15_000L

        fun isVerificationMessage(message: String): Boolean {
            val lower = message.trim().lowercase()
            return lower.startsWith("verifica") || lower.contains("controllo")
        }

        fun create(context: Context): FireAndCheckRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                FireAndCheckDatabase::class.java,
                "fire_and_check.db",
            ).build()
            return FireAndCheckRepository(db)
        }

        fun createInMemory(context: Context): FireAndCheckRepository {
            val db = Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                FireAndCheckDatabase::class.java,
            ).build()
            return FireAndCheckRepository(db)
        }
    }
}
