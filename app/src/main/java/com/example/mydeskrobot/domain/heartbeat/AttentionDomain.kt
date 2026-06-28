package com.example.mydeskrobot.domain.heartbeat

enum class DomainSensitivity {
    LOW,
    MEDIUM,
    HIGH,
}

sealed interface DomainTrigger {
    data class TimeDaily(val hour: Int, val endHourExclusive: Int? = null) : DomainTrigger
    data class TimeWeekly(val dayOfWeek: Int) : DomainTrigger
    data class Event(val eventId: String) : DomainTrigger
}

data class AttentionDomain(
    val id: String,
    val displayName: String,
    val promptAsset: String,
    val trigger: DomainTrigger,
    val sensitivity: DomainSensitivity,
    val isBuiltIn: Boolean = true,
    val requiresPresenceCheck: Boolean = false,
    val canUseCamera: Boolean = false,
)

data class AttentionDomainState(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val userPrompt: String?,
    val trigger: DomainTrigger,
    val sensitivity: DomainSensitivity,
    val lastCheckedAt: Long?,
    val requiresPresenceCheck: Boolean = false,
    val canUseCamera: Boolean = false,
)

data class ProactiveIntervention(
    val domainId: String,
    val topic: String,
    val text: String,
    val outcome: InterventionOutcome,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class InterventionOutcome {
    SPOKE,
    SILENT,
    SUPPRESSED,
    IGNORED,
    POSITIVE_RESPONSE,
}
