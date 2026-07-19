package com.example.mydeskrobot.memory

import java.util.concurrent.TimeUnit

object MemoryReorganizePolicy {

    const val DEFAULT_MIN_USER_FACING_ROWS = 100
    const val DEFAULT_COOLDOWN_DAYS = 7L

    /** @deprecated Use [DEFAULT_MIN_USER_FACING_ROWS] or settings-backed config. */
    @Deprecated("Use DEFAULT_MIN_USER_FACING_ROWS or MemoryReorganizeConfig")
    val MIN_USER_FACING_ROWS: Int get() = DEFAULT_MIN_USER_FACING_ROWS

    /** @deprecated Use settings-backed [MemoryReorganizeConfig.cooldownMs]. */
    @Deprecated("Use MemoryReorganizeConfig.cooldownMs")
    val COOLDOWN_MS: Long get() = TimeUnit.DAYS.toMillis(DEFAULT_COOLDOWN_DAYS)

    sealed interface GateResult {
        data object Allowed : GateResult
        data class TooFew(val count: Int) : GateResult
        data class CooldownActive(val availableAtMs: Long) : GateResult
        data object LlmNotConfigured : GateResult
    }

    fun evaluate(
        userFacingCount: Int,
        lastManualReorganizeAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        llmConfigured: Boolean,
        minUserFacingRows: Int = DEFAULT_MIN_USER_FACING_ROWS,
        cooldownMs: Long = TimeUnit.DAYS.toMillis(DEFAULT_COOLDOWN_DAYS),
    ): GateResult {
        if (!llmConfigured) return GateResult.LlmNotConfigured
        if (userFacingCount < minUserFacingRows) {
            return GateResult.TooFew(userFacingCount)
        }
        if (lastManualReorganizeAtMs != null) {
            val elapsed = nowMs - lastManualReorganizeAtMs
            if (elapsed < cooldownMs) {
                return GateResult.CooldownActive(lastManualReorganizeAtMs + cooldownMs)
            }
        }
        return GateResult.Allowed
    }
}
