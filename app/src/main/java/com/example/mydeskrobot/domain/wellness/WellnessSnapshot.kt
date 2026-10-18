package com.example.mydeskrobot.domain.wellness

/**
 * Persisted SSOT for Wellness v2 (UI, notification, on-demand dialogue).
 * Produced by the DOMAIN_SCORE LLM turn — not a parallel Kotlin scorecard.
 */
data class WellnessSnapshot(
    val computedAtMs: Long,
    val dayKey: String,
    val domains: List<WellnessDomainEntry>,
    val visualOrderDone: Boolean = false,
    val notifySuggested: Boolean = false,
) {
    val needsAttention: Boolean
        get() = domains.any { it.status.needsAttention }

    fun topAttentionEntry(): WellnessDomainEntry? =
        domains
            .filter { it.status.needsAttention }
            .minByOrNull { it.status.ordinal }
            ?: domains.firstOrNull { it.status.needsAttention }

    fun toPromptBlock(): String = buildString {
        appendLine("STATO WELLNESS (autoritativo — ultimo check giornaliero; non ricalcolare):")
        appendLine("- Giorno: $dayKey")
        appendLine("- Calcolato: $computedAtMs")
        if (domains.isEmpty()) {
            appendLine("- Nessun dominio nello snapshot.")
            return@buildString
        }
        domains.forEach { entry ->
            val label = WellnessDomains.DISPLAY_NAMES[entry.id] ?: entry.id
            appendLine("- $label (${entry.status.name.lowercase()}): ${entry.summaryIt}")
        }
        if (needsAttention) {
            appendLine("- Serve attenzione: sì (preferisci notifica già inviata o risposta breve on-demand).")
        }
    }.trimEnd()
}

data class WellnessDomainEntry(
    val id: String,
    val status: WellnessDomainStatus,
    val summaryIt: String,
)

enum class WellnessDomainStatus {
    OK,
    ATTENTION,
    CRITICAL,
    ;

    val needsAttention: Boolean
        get() = this == ATTENTION || this == CRITICAL

    companion object {
        fun fromLlm(raw: String?): WellnessDomainStatus? {
            val key = raw?.trim()?.lowercase() ?: return null
            return when (key) {
                "ok", "fine", "good" -> OK
                "attention", "attenzione", "warn", "warning" -> ATTENTION
                "critical", "critico", "bad" -> CRITICAL
                else -> null
            }
        }
    }
}
