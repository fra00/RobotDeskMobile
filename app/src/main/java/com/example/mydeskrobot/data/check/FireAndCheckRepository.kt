package com.example.mydeskrobot.data.check

import android.content.Context
import androidx.room.Room
import com.example.mydeskrobot.data.check.db.FireAndCheckDatabase
import com.example.mydeskrobot.data.check.db.FireAndCheckEntity
import com.example.mydeskrobot.domain.check.CommitmentFollowUpPolicy
import com.example.mydeskrobot.domain.check.FireAndCheckEntry
import com.example.mydeskrobot.domain.check.FireAndCheckKind
import com.example.mydeskrobot.domain.check.FireAndCheckPhase
import com.example.mydeskrobot.domain.check.resolveFireAndCheckPhase
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
        kind: FireAndCheckKind = FireAndCheckKind.PHYSICAL,
        reRemindCount: Int = 0,
    ) {
        pendingPrimary = PendingPrimary(
            reminderId = reminderId,
            message = primaryMessage,
            dueAtMillis = primaryDueAtMillis,
            kind = kind,
            reRemindCount = reRemindCount,
        )

        if (fireAndCheck ||
            kind == FireAndCheckKind.COMMITMENT ||
            !checkGoal.isNullOrBlank() ||
            !triggerReason.isNullOrBlank()
        ) {
            insertPrimaryEntity(
                reminderId = reminderId,
                primaryMessage = primaryMessage,
                primaryDueAtMillis = primaryDueAtMillis,
                checkGoal = checkGoal,
                triggerReason = triggerReason,
                kind = kind,
                reRemindCount = reRemindCount,
            )
        }
    }

    suspend fun onVerificationReminderScheduled(
        verificationReminderId: Long,
        verificationMessage: String,
        verificationDueAtMillis: Long,
    ) {
        val kindHint = if (CommitmentFollowUpPolicy.isCommitmentVerificationMessage(verificationMessage)) {
            FireAndCheckKind.COMMITMENT
        } else {
            null
        }
        val primary = dao.findLatestAwaitingVerificationLink()
            ?: pendingPrimary?.let { pending ->
                insertPrimaryEntity(
                    reminderId = pending.reminderId,
                    primaryMessage = pending.message,
                    primaryDueAtMillis = pending.dueAtMillis,
                    checkGoal = null,
                    triggerReason = pending.message,
                    kind = kindHint ?: pending.kind,
                    reRemindCount = pending.reRemindCount,
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
            kind = kindHint ?: primary.kind,
        )
        dao.update(updated)
    }

    /**
     * Updates verification due time / reminder id after a speak-gate deferral.
     */
    suspend fun updateVerificationSchedule(
        verificationReminderId: Long,
        newVerificationReminderId: Long,
        newDueAtMillis: Long,
    ) {
        val entry = dao.findByVerificationReminderId(verificationReminderId) ?: return
        dao.update(
            entry.copy(
                verificationReminderId = newVerificationReminderId,
                verificationDueAtMillis = newDueAtMillis,
            ),
        )
    }

    suspend fun findByVerificationReminderId(reminderId: Long): FireAndCheckEntity? =
        dao.findByVerificationReminderId(reminderId)

    suspend fun findByPrimaryReminderId(reminderId: Long): FireAndCheckEntity? =
        dao.findByPrimaryReminderId(reminderId)

    /**
     * Prepares a new commitment follow-up loop.
     * @return reRemindCount for the new entity, or null if capped (schedule plain reminder only).
     */
    suspend fun prepareNewCommitmentFollowUp(): Int? {
        val active = dao.listByStatus(FireAndCheckStatus.ACTIVE)
            .filter { it.kind == FireAndCheckKind.COMMITMENT }
            .maxByOrNull { it.createdAtMillis }
        if (active == null) return 0
        if (!CommitmentFollowUpPolicy.canReRemind(active.reRemindCount)) return null
        dao.updateStatus(active.id, FireAndCheckStatus.COMPLETED)
        return active.reRemindCount + 1
    }

    private suspend fun insertPrimaryEntity(
        reminderId: Long,
        primaryMessage: String,
        primaryDueAtMillis: Long,
        checkGoal: String?,
        triggerReason: String?,
        kind: FireAndCheckKind,
        reRemindCount: Int,
    ) {
        if (dao.findByPrimaryReminderId(reminderId) != null) return

        val reason = triggerReason?.trim().orEmpty().ifBlank { primaryMessage }
        dao.insert(
            FireAndCheckEntity(
                kind = kind,
                triggerReason = reason,
                checkGoal = checkGoal?.trim()?.takeIf { it.isNotBlank() },
                primaryMessage = primaryMessage,
                primaryReminderId = reminderId,
                verificationMessage = null,
                verificationReminderId = null,
                primaryDueAtMillis = primaryDueAtMillis,
                verificationDueAtMillis = null,
                reRemindCount = reRemindCount,
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

    /**
     * Cancels an active fire-and-check loop by id.
     * @return reminder ids that should also be cancelled in [ScheduledTaskRepository], if any.
     */
    suspend fun cancelById(id: Long): CancelledLoopReminders? {
        val entry = dao.getById(id) ?: return null
        if (entry.status != FireAndCheckStatus.ACTIVE) {
            return CancelledLoopReminders(
                primaryReminderId = entry.primaryReminderId,
                verificationReminderId = entry.verificationReminderId,
            )
        }
        dao.updateStatus(entry.id, FireAndCheckStatus.CANCELLED)
        return CancelledLoopReminders(
            primaryReminderId = entry.primaryReminderId,
            verificationReminderId = entry.verificationReminderId,
        )
    }

    data class CancelledLoopReminders(
        val primaryReminderId: Long?,
        val verificationReminderId: Long?,
    ) {
        fun reminderIds(): List<Long> =
            listOfNotNull(primaryReminderId, verificationReminderId).distinct()
    }

    private fun FireAndCheckEntity.toEntry(): FireAndCheckEntry {
        val now = System.currentTimeMillis()
        val phase = resolveFireAndCheckPhase(
            nowMillis = now,
            primaryDueAtMillis = primaryDueAtMillis,
            verificationDueAtMillis = verificationDueAtMillis,
            primaryFiredAtMillis = primaryFiredAtMillis,
        )
        return FireAndCheckEntry(
            id = id,
            kind = kind,
            triggerReason = triggerReason,
            checkGoal = checkGoal,
            primaryMessage = primaryMessage,
            verificationMessage = verificationMessage,
            primaryDueAtMillis = primaryDueAtMillis,
            verificationDueAtMillis = verificationDueAtMillis,
            phase = phase,
            reRemindCount = reRemindCount,
        )
    }

    private data class PendingPrimary(
        val reminderId: Long,
        val message: String,
        val dueAtMillis: Long,
        val kind: FireAndCheckKind = FireAndCheckKind.PHYSICAL,
        val reRemindCount: Int = 0,
    )

    companion object {
        private const val ACTIVE_TICK_MS = 15_000L

        fun isVerificationMessage(message: String): Boolean {
            if (CommitmentFollowUpPolicy.isCommitmentVerificationMessage(message)) return true
            val lower = message.trim().lowercase()
            return lower.startsWith("verifica") || lower.contains("controllo")
        }

        fun create(context: Context): FireAndCheckRepository {
            val db = Room.databaseBuilder(
                context.applicationContext,
                FireAndCheckDatabase::class.java,
                "fire_and_check.db",
            )
                .addMigrations(FireAndCheckDatabase.MIGRATION_1_2)
                .build()
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
