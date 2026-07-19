package com.example.mydeskrobot.domain.wellness

/**
 * Care domains scored in the unified Wellness check.
 * Enable/disable toggles control which are evaluated; scheduling is Wellness-owned.
 */
object WellnessDomains {
    const val MEALS = "pasti"
    const val MOVEMENT = "attivita_fisica"
    const val WORKLOAD = "carico_lavoro"
    const val SOCIAL = "contatti_sociali"
    const val ORDER = "ordine_ambiente"

    val ALL: Set<String> = setOf(MEALS, MOVEMENT, WORKLOAD, SOCIAL, ORDER)

    val DISPLAY_NAMES: Map<String, String> = mapOf(
        MEALS to "Pasti",
        MOVEMENT to "Attività fisica",
        WORKLOAD to "Carico di lavoro",
        SOCIAL to "Contatti sociali",
        ORDER to "Ordine ambiente",
    )
}
