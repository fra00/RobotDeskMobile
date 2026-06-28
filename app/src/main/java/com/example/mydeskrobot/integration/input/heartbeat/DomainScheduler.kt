package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.domain.heartbeat.AttentionDomainState
import com.example.mydeskrobot.domain.heartbeat.DomainTrigger
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

class DomainEventBus {
    private val pendingEvents = ConcurrentHashMap<String, Long>()

    fun fire(eventId: String) {
        pendingEvents[eventId] = System.currentTimeMillis()
    }

    fun consume(eventId: String): Boolean {
        val removed = pendingEvents.remove(eventId)
        return removed != null
    }
}

class DomainScheduler(
    private val eventBus: DomainEventBus,
) {
    private var roundRobinIndex = 0

    fun resetRoundRobin() {
        roundRobinIndex = 0
    }

    fun nextDueDomain(
        domains: List<AttentionDomainState>,
        now: Calendar = Calendar.getInstance(),
    ): AttentionDomainState? {
        val enabled = domains.filter { it.enabled }
        if (enabled.isEmpty()) return null

        val due = enabled.filter { isDue(it, now) }
        if (due.isEmpty()) return null

        val index = roundRobinIndex % due.size
        roundRobinIndex = (roundRobinIndex + 1) % Int.MAX_VALUE
        return due[index]
    }

    /** Debug: round-robin any enabled domain, ignoring schedule. */
    fun nextDomainForDebug(domains: List<AttentionDomainState>): AttentionDomainState? {
        val enabled = domains.filter { it.enabled }
        if (enabled.isEmpty()) return null
        val index = roundRobinIndex % enabled.size
        roundRobinIndex = (roundRobinIndex + 1) % Int.MAX_VALUE
        return enabled[index]
    }

    private fun isDue(domain: AttentionDomainState, now: Calendar): Boolean {
        return when (val trigger = domain.trigger) {
            is DomainTrigger.Event -> eventBus.consume(trigger.eventId)
            is DomainTrigger.TimeDaily -> isDailyDue(trigger, now)
            is DomainTrigger.TimeWeekly -> isWeeklyDue(trigger, now)
        }
    }

    private fun isDailyDue(trigger: DomainTrigger.TimeDaily, now: Calendar): Boolean {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val end = trigger.endHourExclusive
        return if (end != null) {
            hour in trigger.hour until end
        } else {
            hour == trigger.hour
        }
    }

    private fun isWeeklyDue(trigger: DomainTrigger.TimeWeekly, now: Calendar): Boolean {
        return now.get(Calendar.DAY_OF_WEEK) == trigger.dayOfWeek &&
            now.get(Calendar.HOUR_OF_DAY) >= 10
    }
}
