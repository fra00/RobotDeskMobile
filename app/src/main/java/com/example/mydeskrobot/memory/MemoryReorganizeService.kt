package com.example.mydeskrobot.memory

import android.util.Log
import com.example.mydeskrobot.memory.consolidate.MemoryConsolidationResult
import com.example.mydeskrobot.memory.consolidate.MemoryConsolidationService
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MemoryReorganizeService(
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
    private val consolidationService: MemoryConsolidationService,
    private val settingsRepository: MemoryReorganizeSettingsStore,
    private val llmConfigured: () -> Boolean,
) {
    private val runMutex = Mutex()

    suspend fun runAutoIfDue(): MemoryReorganizeOutcome {
        val settings = settingsRepository.loadReorganizeConfig()
        if (!settings.autoReorganizeEnabled) {
            return MemoryReorganizeOutcome.SkippedAutoDisabled
        }
        return runInternal(force = false, settings = settings, logTag = "auto")
    }

    suspend fun runManual(forceConsolidation: Boolean = true): MemoryReorganizeOutcome {
        val settings = settingsRepository.loadReorganizeConfig()
        return runInternal(force = forceConsolidation, settings = settings, logTag = "manual")
    }

    private suspend fun runInternal(
        force: Boolean,
        settings: MemoryReorganizeConfig,
        logTag: String,
    ): MemoryReorganizeOutcome = runMutex.withLock {
        unifiedMemoryRepository.ensureMigrated()
        val userFacingCount = unifiedMemoryRepository.getUserFacingActiveDocuments().size
        val lastReorganizeAt = settingsRepository.getLastManualReorganizeAtMs()
        val minRows = settings.minUserFacingRows
        val cooldownMs = settings.cooldownMs

        when (
            val gate = MemoryReorganizePolicy.evaluate(
                userFacingCount = userFacingCount,
                lastManualReorganizeAtMs = lastReorganizeAt,
                llmConfigured = llmConfigured(),
                minUserFacingRows = minRows,
                cooldownMs = cooldownMs,
            )
        ) {
            MemoryReorganizePolicy.GateResult.LlmNotConfigured ->
                return@withLock MemoryReorganizeOutcome.GateLlmNotConfigured
            is MemoryReorganizePolicy.GateResult.TooFew ->
                return@withLock MemoryReorganizeOutcome.GateTooFew(gate.count, minRows)
            is MemoryReorganizePolicy.GateResult.CooldownActive ->
                return@withLock MemoryReorganizeOutcome.GateCooldown(gate.availableAtMs)
            MemoryReorganizePolicy.GateResult.Allowed -> Unit
        }

        when (
            val consolidation = consolidationService.consolidateIfNeeded(
                force = force,
                minRowsToConsolidate = minRows,
            )
        ) {
            is MemoryConsolidationResult.Success -> {
                val pruned = unifiedMemoryRepository.reorganize()
                settingsRepository.setLastManualReorganizeAtMs(System.currentTimeMillis())
                Log.i(
                    TAG,
                    "Reorganize ($logTag) success: ${consolidation.before} -> ${consolidation.after}, pruned=$pruned",
                )
                MemoryReorganizeOutcome.Success(
                    before = consolidation.before,
                    after = consolidation.after,
                    pruned = pruned,
                )
            }
            MemoryConsolidationResult.SkippedUnchanged -> {
                val pruned = unifiedMemoryRepository.reorganize()
                settingsRepository.setLastManualReorganizeAtMs(System.currentTimeMillis())
                Log.i(TAG, "Reorganize ($logTag) unchanged; pruned=$pruned")
                MemoryReorganizeOutcome.Unchanged(pruned = pruned)
            }
            MemoryConsolidationResult.SkippedNotConfigured ->
                MemoryReorganizeOutcome.GateLlmNotConfigured
            is MemoryConsolidationResult.SkippedTooFew ->
                MemoryReorganizeOutcome.GateTooFew(consolidation.count, minRows)
            MemoryConsolidationResult.SkippedAlreadyRunning ->
                MemoryReorganizeOutcome.AlreadyRunning
            is MemoryConsolidationResult.Failed -> {
                Log.w(TAG, "Reorganize ($logTag) failed: ${consolidation.reason}")
                MemoryReorganizeOutcome.Failed(consolidation.reason)
            }
        }
    }

    companion object {
        private const val TAG = "MemoryReorganize"
    }
}

sealed class MemoryReorganizeOutcome {
    data object SkippedAutoDisabled : MemoryReorganizeOutcome()
    data object GateLlmNotConfigured : MemoryReorganizeOutcome()
    data class GateTooFew(val count: Int, val minRequired: Int) : MemoryReorganizeOutcome()
    data class GateCooldown(val availableAtMs: Long) : MemoryReorganizeOutcome()
    data object AlreadyRunning : MemoryReorganizeOutcome()
    data class Success(val before: Int, val after: Int, val pruned: Int) : MemoryReorganizeOutcome()
    data class Unchanged(val pruned: Int) : MemoryReorganizeOutcome()
    data class Failed(val reason: String) : MemoryReorganizeOutcome()
}

data class MemoryReorganizeConfig(
    val autoReorganizeEnabled: Boolean = true,
    val minUserFacingRows: Int = MemoryReorganizePolicy.DEFAULT_MIN_USER_FACING_ROWS,
    val cooldownDays: Long = MemoryReorganizePolicy.DEFAULT_COOLDOWN_DAYS,
) {
    val cooldownMs: Long get() = java.util.concurrent.TimeUnit.DAYS.toMillis(cooldownDays.coerceAtLeast(1L))
}
