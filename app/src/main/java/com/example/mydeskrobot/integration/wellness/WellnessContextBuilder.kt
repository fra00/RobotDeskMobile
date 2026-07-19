package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.wellness.WellnessCustomDomain
import com.example.mydeskrobot.domain.wellness.WellnessDomains
import com.example.mydeskrobot.domain.wellness.WellnessPhase
import com.example.mydeskrobot.memory.db.MemoryCategory
import com.example.mydeskrobot.memory.unified.UnifiedMemoryRepository
import com.example.mydeskrobot.reasoning.model.RobotInput
import java.text.SimpleDateFormat
import java.util.Locale

class WellnessContextBuilder(
    private val activityLogRepository: ActivityLogRepository,
    private val unifiedMemoryRepository: UnifiedMemoryRepository,
) {
    suspend fun build(
        phase: WellnessPhase,
        bodyConfigured: Boolean,
        bodyReachable: Boolean,
        enabledDomainIds: Set<String> = WellnessDomains.ALL,
        customDomains: List<WellnessCustomDomain> = emptyList(),
    ): RobotInput.WellnessCheck {
        val habitSummary = activityLogRepository.getHabitSummary()?.summaryText
        val recentActivities = activityLogRepository
            .getRecentPhysicalForContext(maxEvents = 12, daysBack = 1)
            .map { event ->
                val time = activityTimeFormat.format(event.timestampMs)
                "$time ${event.label}"
            }
        val activePatterns = unifiedMemoryRepository
            .getToolByCategory(MemoryCategory.PATTERN, limit = 8)
            .map { it.value }
        val recentObservations = unifiedMemoryRepository
            .getRecentObservations(limit = 6)
            .map { it.value }
        val orderObservation = if (
            phase == WellnessPhase.DOMAIN_SCORE &&
            WellnessDomains.ORDER in enabledDomainIds
        ) {
            findFreshOrderObservation(recentObservations)
        } else {
            null
        }

        return RobotInput.WellnessCheck(
            phase = phase,
            enabledDomainIds = enabledDomainIds,
            customDomains = customDomains.filter { it.id in enabledDomainIds },
            habitProfileSummary = habitSummary,
            recentDailyActivities = recentActivities,
            activePatterns = activePatterns,
            recentObservations = recentObservations,
            orderObservationFresh = orderObservation,
            bodyConfigured = bodyConfigured,
            bodyReachable = bodyReachable,
        )
    }

    private fun findFreshOrderObservation(observations: List<String>): String? =
        observations.firstOrNull { obs ->
            obs.contains("ordine", ignoreCase = true) ||
                obs.contains("disordin", ignoreCase = true) ||
                obs.contains("scrivania", ignoreCase = true)
        }

    companion object {
        private val activityTimeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)
    }
}
