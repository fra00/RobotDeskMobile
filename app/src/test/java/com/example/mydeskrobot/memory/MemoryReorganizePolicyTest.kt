package com.example.mydeskrobot.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

import java.util.concurrent.TimeUnit

class MemoryReorganizePolicyTest {

    @Test
    fun evaluate_allows_when_count_and_cooldown_ok() {
        val result = MemoryReorganizePolicy.evaluate(
            userFacingCount = 100,
            lastManualReorganizeAtMs = null,
            nowMs = 1_000_000L,
            llmConfigured = true,
        )
        assertEquals(MemoryReorganizePolicy.GateResult.Allowed, result)
    }

    @Test
    fun evaluate_blocks_when_too_few_rows() {
        val result = MemoryReorganizePolicy.evaluate(
            userFacingCount = 99,
            lastManualReorganizeAtMs = null,
            nowMs = 1_000_000L,
            llmConfigured = true,
        )
        assertTrue(result is MemoryReorganizePolicy.GateResult.TooFew)
        assertEquals(99, (result as MemoryReorganizePolicy.GateResult.TooFew).count)
    }

    @Test
    fun evaluate_blocks_during_cooldown() {
        val last = 1_000_000L
        val cooldownMs = TimeUnit.DAYS.toMillis(7)
        val result = MemoryReorganizePolicy.evaluate(
            userFacingCount = 120,
            lastManualReorganizeAtMs = last,
            nowMs = last + cooldownMs - 1,
            llmConfigured = true,
            cooldownMs = cooldownMs,
        )
        assertTrue(result is MemoryReorganizePolicy.GateResult.CooldownActive)
        assertEquals(
            last + cooldownMs,
            (result as MemoryReorganizePolicy.GateResult.CooldownActive).availableAtMs,
        )
    }

    @Test
    fun evaluate_blocks_when_llm_not_configured() {
        val result = MemoryReorganizePolicy.evaluate(
            userFacingCount = 200,
            lastManualReorganizeAtMs = null,
            llmConfigured = false,
        )
        assertEquals(MemoryReorganizePolicy.GateResult.LlmNotConfigured, result)
    }

    @Test
    fun evaluate_respects_custom_min_rows_and_cooldown() {
        val cooldownMs = TimeUnit.DAYS.toMillis(3)
        val result = MemoryReorganizePolicy.evaluate(
            userFacingCount = 50,
            lastManualReorganizeAtMs = null,
            llmConfigured = true,
            minUserFacingRows = 50,
            cooldownMs = cooldownMs,
        )
        assertEquals(MemoryReorganizePolicy.GateResult.Allowed, result)
    }
}
