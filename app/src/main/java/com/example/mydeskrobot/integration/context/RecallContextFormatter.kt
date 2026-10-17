package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.data.predictivity.HabitSlotCodec
import com.example.mydeskrobot.domain.list.ListItemType
import com.example.mydeskrobot.memory.db.MemoryCategory
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
            appendLine("MEMORIA (usa ciò che serve per la domanda; per domande ampie sintetizza tutte le sezioni rilevanti):")
            appendLine("EPISODI = avvenuto (unica prova di cosa è successo); FATTI = stabili; ROUTINE DICHIARATE / ABITUDINI ATTESE / PROFILO ABITUDINI = atteso, non attività fatte.")
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
                appendLine("(pattern tipici, non prova di ciò che è successo in un giorno/settimana)")
                summaries.forEach { summary ->
                    appendLine(summary.value.trim())
                }
            }

            appendAutonomySections(grouped[MemoryDocumentKind.AUTONOMY].orEmpty())

            val userFacts = grouped[MemoryDocumentKind.USER_FACT].orEmpty()
            val routines = userFacts.filter { isRoutineCategory(it.category) }
            val stableFacts = userFacts.filterNot { isRoutineCategory(it.category) }

            if (routines.isNotEmpty()) {
                appendLine()
                appendLine("ROUTINE DICHIARATE:")
                appendLine("(orari/schemi dichiarati dall'utente — non episodi Log Day / non prova di 'questa settimana')")
                routines.forEachIndexed { index, fact ->
                    appendLine("${index + 1}. ${fact.value}")
                }
            }

            if (stableFacts.isNotEmpty()) {
                appendLine()
                appendLine("FATTI:")
                stableFacts.forEachIndexed { index, fact ->
                    val categoryLabel = fact.category ?: fact.kind
                    appendLine("${index + 1}. ($categoryLabel) ${fact.value}")
                }
            }
        }.trim()
    }

    private fun StringBuilder.appendAutonomySections(autonomyDocs: List<MemoryDocumentEntity>) {
        if (autonomyDocs.isEmpty()) return

        val expectedHabits = mutableListOf<String>()
        val robotNotes = mutableListOf<String>()

        autonomyDocs.forEach { doc ->
            val slot = HabitSlotCodec.decode(doc.value)
            if (slot != null) {
                expectedHabits.add("- ${HabitSlotCodec.formatForPrompt(slot)}")
                return@forEach
            }
            val category = doc.category?.uppercase(Locale.ROOT).orEmpty()
            when (category) {
                MemoryCategory.PATTERN.name ->
                    expectedHabits.add("- ${doc.value.trim()}")
                MemoryCategory.OBSERVATION.name,
                MemoryCategory.INTENT.name,
                ->
                    robotNotes.add("- ($category) ${doc.value.trim()}")
                else ->
                    robotNotes.add("- (${doc.category ?: doc.kind}) ${doc.value.trim()}")
            }
        }

        if (expectedHabits.isNotEmpty()) {
            appendLine()
            appendLine("ABITUDINI ATTESE:")
            appendLine("(pattern attesi, non attività registrate — non usare come prova di ciò che è successo)")
            expectedHabits.forEach { appendLine(it) }
        }
        if (robotNotes.isNotEmpty()) {
            appendLine()
            appendLine("NOTE ROBOT:")
            appendLine("(interne, non fatti utente né attività fatte)")
            robotNotes.forEach { appendLine(it) }
        }
    }

    private fun isRoutineCategory(category: String?): Boolean =
        category.equals(MemoryCategory.ROUTINE.name, ignoreCase = true)

    private fun kindOf(document: MemoryDocumentEntity): MemoryDocumentKind =
        runCatching { MemoryDocumentKind.valueOf(document.kind) }
            .getOrDefault(MemoryDocumentKind.USER_FACT)
}
