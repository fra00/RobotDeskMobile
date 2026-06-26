package com.example.mydeskrobot.integration.context

import com.example.mydeskrobot.domain.activitylog.ActivityLogEntry
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.memory.unified.db.MemoryDocumentEntity
import java.text.SimpleDateFormat
import java.util.Locale

internal object DayContextFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ITALY)

    fun formatReminderLine(scheduledAtMs: Long?, message: String): String {
        val time = scheduledAtMs?.let { timeFormat.format(it) }.orEmpty()
        return if (time.isBlank()) "- $message" else "- $time $message"
    }

    fun formatEpisodeLine(episode: ActivityLogEntry): String {
        val timePrefix = episode.scheduledAtMs?.let { "${timeFormat.format(it)} " }.orEmpty()
        val confidenceLabel = formatConfidenceLabel(episode.confidence)
        val channelSuffix = episode.sourceChannel?.let { ", $it" }.orEmpty()
        val actorPrefix = episode.actor?.let { "$it: " }.orEmpty()
        val snippet = episode.rawPhrase?.let { " — \"$it\"" }.orEmpty()
        val kindSuffix = formatKindSuffix(episode.eventKind)
        return "$timePrefix$actorPrefix${episode.label}$snippet$kindSuffix ($confidenceLabel$channelSuffix)"
    }

    fun formatEpisodeDocument(document: MemoryDocumentEntity): String {
        val timePrefix = document.scheduledAtMs?.let { "${timeFormat.format(it)} " }.orEmpty()
        val confidence = runCatching {
            EpisodeConfidence.valueOf(document.episodeConfidence.orEmpty())
        }.getOrNull() ?: EpisodeConfidence.CONFIRMED
        val confidenceLabel = formatConfidenceLabel(confidence)
        val channelSuffix = document.sourceChannel?.let { ", $it" }.orEmpty()
        val actorPrefix = document.actor?.let { "$it: " }.orEmpty()
        val episodeKind = runCatching { EpisodeKind.valueOf(document.category.orEmpty()) }.getOrNull()
        val kindSuffix = episodeKind?.let { formatKindSuffix(it) }.orEmpty()
        return "$timePrefix$actorPrefix${document.value}$kindSuffix ($confidenceLabel$channelSuffix)"
    }

    fun parseSpatialPlaceLabel(value: String): String =
        value.substringBefore(":").substringBefore(" —").trim()

    fun parseCurrentPlaceLabel(value: String): String =
        value.removePrefix("Stanza corrente:").trim()

    private fun formatConfidenceLabel(confidence: EpisodeConfidence): String =
        when (confidence) {
            EpisodeConfidence.TENTATIVE -> "tentativo"
            EpisodeConfidence.CONFIRMED -> "confermato"
        }

    private fun formatKindSuffix(eventKind: EpisodeKind): String {
        val kindHint = when (eventKind) {
            EpisodeKind.SOCIAL_THREAD -> "conversazione aperta"
            EpisodeKind.COMMITMENT -> "impegno"
            EpisodeKind.PLAN -> "piano"
            EpisodeKind.PHYSICAL_NOW -> ""
        }
        return kindHint.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    }
}
