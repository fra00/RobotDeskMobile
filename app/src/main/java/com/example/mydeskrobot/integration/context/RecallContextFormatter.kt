package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.memory.unified.MemoryDocumentKind
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import com.example.mydeskrobot.reasoning.memory.TemporalScope
import com.example.mydeskrobot.reasoning.planning.PlanningDayResolver
import java.text.SimpleDateFormat
import java.util.Locale

internal object RecallContextFormatter {

    private val episodeTimeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)

    fun formatRecallBlock(
        documents: List<MemoryDocumentEntity>,
        focusDayKey: String? = null,
        temporalScope: TemporalScope = TemporalScope.NONE,
    ): String {
        if (documents.isEmpty()) return ""

        val unreadEpisodes = documents.filter { kindOf(it) == MemoryDocumentKind.EPISODE && it.isUnread }
        val recallDocuments = documents.filterNot { doc ->
            kindOf(doc) == MemoryDocumentKind.EPISODE && doc.isUnread
        }

        val grouped = recallDocuments.groupBy { kindOf(it) }
        return buildString {
            appendLine("MEMORIA (usa ciò che serve per la domanda; ignora il resto):")
            appendLine("EPISODI includono messaggi e notifiche archiviati.")
            when (temporalScope) {
                TemporalScope.WEEK -> appendLine("Contesto: questa settimana")
                TemporalScope.MONTH -> appendLine("Contesto: questo mese")
                TemporalScope.SINGLE_DAY, TemporalScope.NONE -> focusDayKey?.let { dayKey ->
                    appendLine("Contesto giorno: ${PlanningDayResolver.formatDayLabel(dayKey)}")
                }
            }

            if (unreadEpisodes.isNotEmpty()) {
                appendLine()
                appendLine("NOTIFICHE_NON_LETTE:")
                unreadEpisodes.forEach { episode ->
                    val time = episodeTimeFormat.format(episode.scheduledAtMs ?: episode.createdAt)
                    appendLine("- $time ${DayContextFormatter.formatEpisodeDocument(episode)} [non letto]")
                }
            }

            grouped[MemoryDocumentKind.EPISODE]?.let { episodes ->
                appendLine()
                appendLine("EPISODI:")
                episodes.forEach { episode ->
                    val time = episodeTimeFormat.format(episode.scheduledAtMs ?: episode.createdAt)
                    appendLine("- $time ${DayContextFormatter.formatEpisodeDocument(episode)}")
                }
            }

            grouped[MemoryDocumentKind.REMINDER]?.let { reminders ->
                appendLine()
                appendLine("PROMEMORIA:")
                reminders.forEach { reminder ->
                    appendLine(DayContextFormatter.formatReminderLine(reminder.scheduledAtMs, reminder.value))
                }
            }

            grouped[MemoryDocumentKind.LIST_ITEM]?.let { items ->
                appendLine()
                appendLine("LISTE:")
                items.forEach { item ->
                    val typeLabel = item.category ?: ListItemType.NOTE.name
                    appendLine("- $typeLabel: ${item.value}")
                }
            }

            grouped[MemoryDocumentKind.SPATIAL]?.let { places ->
                appendLine()
                appendLine("SPAZIO:")
                places.forEach { place ->
                    appendLine("- ${place.value}")
                }
            }

            grouped[MemoryDocumentKind.HABIT_SUMMARY]?.let { summaries ->
                appendLine()
                appendLine("PROFILO ABITUDINI:")
                summaries.forEach { summary ->
                    appendLine(summary.value.trim())
                }
            }

            val userFacts = grouped[MemoryDocumentKind.USER_FACT].orEmpty() +
                grouped[MemoryDocumentKind.AUTONOMY].orEmpty()
            if (userFacts.isNotEmpty()) {
                appendLine()
                appendLine("FATTI:")
                userFacts.forEachIndexed { index, fact ->
                    val categoryLabel = fact.category ?: fact.kind
                    appendLine("${index + 1}. ($categoryLabel) ${fact.value}")
                }
            }
        }.trim()
    }

    private fun kindOf(document: MemoryDocumentEntity): MemoryDocumentKind =
        runCatching { MemoryDocumentKind.valueOf(document.kind) }
            .getOrDefault(MemoryDocumentKind.USER_FACT)
}
