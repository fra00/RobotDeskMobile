package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.domain.heartbeat.AttentionDomainState
import com.example.mydeskrobot.domain.heartbeat.DomainSensitivity
import com.example.mydeskrobot.domain.heartbeat.DomainTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class DomainSchedulerTest {

    private val eventBus = DomainEventBus()
    private val scheduler = DomainScheduler(eventBus)

    @Test
    fun nextDueDomain_roundRobinBetweenDueDomains() {
        val domains = listOf(
            domain("a", DomainTrigger.TimeDaily(hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY))),
            domain("b", DomainTrigger.TimeDaily(hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY))),
        )

        val first = scheduler.nextDueDomain(domains)
        val second = scheduler.nextDueDomain(domains)
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(setOf("a", "b"), setOf(first!!.id, second!!.id))
        assertEquals(false, first.id == second.id)
    }

    @Test
    fun nextDueDomain_eventTrigger() {
        eventBus.fire("nuova_foto")
        val domains = listOf(
            domain("ordine", DomainTrigger.Event("nuova_foto")),
        )
        val due = scheduler.nextDueDomain(domains)
        assertNotNull(due)
        assertEquals("ordine", due!!.id)
        assertNull(scheduler.nextDueDomain(domains))
    }

    @Test
    fun nextDomainForDebug_ignoresSchedule() {
        val domains = listOf(
            domain("a", DomainTrigger.TimeDaily(hour = 3)),
            domain("b", DomainTrigger.TimeDaily(hour = 4)),
        )
        val picked = scheduler.nextDomainForDebug(domains)
        assertNotNull(picked)
        assertEquals("a", picked!!.id)
    }

    private fun domain(id: String, trigger: DomainTrigger): AttentionDomainState =
        AttentionDomainState(
            id = id,
            displayName = id,
            enabled = true,
            isBuiltIn = true,
            userPrompt = null,
            trigger = trigger,
            sensitivity = DomainSensitivity.MEDIUM,
            lastCheckedAt = null,
        )
}
