package com.example.mydeskrobot.domain.spatial

import java.util.Locale

object SpatialPromptFormatter {

    fun format(
        context: SpatialContextSnapshot,
        knownPlaceLabels: List<String>,
        options: SpatialFormatOptions = SpatialFormatOptions(),
    ): String = buildString {
        appendLine("DOVE SONO (autoritativo — identità stanza, NON descrizione visiva corrente):")
        when {
            context.currentPlaceLabel != null -> {
                appendLine("- Stanza corrente: ${context.currentPlaceLabel} (confidenza ${formatConfidence(context.confidence)})")
                context.roomType?.let { appendLine("- Tipo: ${RoomType.displayLabel(it)}") }
            }
            context.resolution == SpatialResolution.UNKNOWN -> appendLine("- Stanza corrente: sconosciuta")
            else -> appendLine("- Stanza corrente: non ancora identificata")
        }
        if (!options.identityOnly) {
            context.lastLandmarks.takeIf { it.isNotEmpty() }?.let { landmarks ->
                appendLine("- Landmark ultimo scan (storico, non foto corrente): ${landmarks.joinToString(", ")}")
            }
        }
        if (knownPlaceLabels.isNotEmpty()) {
            appendLine("- Luoghi noti: ${knownPlaceLabels.joinToString(", ")}")
        } else {
            appendLine("- Luoghi noti: nessuno memorizzato")
        }
        if (options.identityOnly) {
            appendLine("- Per \"dove siamo\": rispondi dalla stanza corrente sopra; non descrivere oggetti visibili senza foto fresca.")
        } else {
            appendLine("- Regola match: una foto + match_place; se confidenza alta (≥0.55) basta, se bassa/media → scan multi-angolo (se corpo disponibile).")
            appendLine("- Regola memorizza: stanza nuova o save_place → scan multi-angolo obbligatorio (se corpo disponibile), poi save_place.")
        }
        appendLine("- Presenza utente è separata (detect_presence); stanza ≠ presenza.")
    }.trim()

    private fun formatConfidence(value: Float): String =
        String.format(Locale.US, "%.2f", value.coerceIn(0f, 1f))
}

data class SpatialFormatOptions(
    val identityOnly: Boolean = false,
)

data class SpatialContextSnapshot(
    val currentPlaceId: Long? = null,
    val currentPlaceLabel: String? = null,
    val roomType: RoomType? = null,
    val confidence: Float = 0f,
    val resolution: SpatialResolution = SpatialResolution.UNKNOWN,
    val lastLandmarks: List<String> = emptyList(),
)
