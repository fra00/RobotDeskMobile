package com.example.mydeskrobot.activity.extract

import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.domain.activitylog.EpisodeConfidence
import com.example.mydeskrobot.domain.activitylog.EpisodeKind
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.memory.extract.ChatLogEntry
import com.example.mydeskrobot.memory.extract.MemoryExtractionService
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal data class EpisodicExtractionPayload(
    val events: List<EpisodicEventPayload> = emptyList(),
)

internal data class EpisodicEventPayload(
    val kind: String? = null,
    val label: String? = null,
    val raw_phrase: String? = null,
    val confidence: String? = null,
    val scheduled_day: String? = null,
    val scheduled_time: String? = null,
    val actor: String? = null,
    val source_channel: String? = null,
)

class ActivityExtractionService(
    private val llmClient: LlmClient,
    private val activityLogRepository: ActivityLogRepository,
    private val extractorPrompt: String,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(EpisodicExtractionPayload::class.java)

    suspend fun processDelta(newEntries: List<ChatLogEntry>): Int {
        if (newEntries.isEmpty()) return 0

        val transcript = newEntries.joinToString("\n") { entry ->
            "${entry.role.uppercase()}: ${entry.text}"
        }

        val llmResult = llmClient.chat(
            messages = listOf(ConversationMessage.User(transcript)),
            systemPrompt = extractorPrompt,
        )
        llmResult.exceptionOrNull()?.let { error ->
            Log.w(TAG, "Episodic extractor LLM failed: ${LlmHttpErrors.formatForLog(error)}", error)
            return 0
        }
        val raw = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (raw.isBlank()) {
            Log.w(TAG, "Episodic extractor returned empty content")
            return 0
        }

        val payload = parsePayload(raw)
        if (payload == null) {
            Log.w(TAG, "Episodic extractor JSON parse failed: ${raw.take(120)}")
            return 0
        }
        if (payload.events.isEmpty()) {
            Log.d(TAG, "Episodic extractor returned no events")
            return 0
        }

        var saved = 0
        payload.events.forEach { item ->
            val label = item.label?.trim().orEmpty()
            if (label.isBlank()) return@forEach
            val eventKind = parseKind(item.kind)
            val confidence = parseConfidence(item.confidence)
            val scheduledDayKey = item.scheduled_day?.trim()?.takeIf { it.isNotBlank() }
            val scheduledAtMs = ActivityLogRepository.parseScheduledAtMs(
                scheduledDayKey = scheduledDayKey,
                scheduledTime = item.scheduled_time,
            )
            val id = if (eventKind == EpisodeKind.PHYSICAL_NOW) {
                activityLogRepository.appendEvent(
                    label = label,
                    rawPhrase = item.raw_phrase?.trim()?.takeIf { it.isNotBlank() },
                    source = ActivitySource.EXTRACTOR,
                    eventKind = eventKind,
                    confidence = confidence,
                    scheduledAtMs = scheduledAtMs,
                    scheduledDayKey = scheduledDayKey,
                    actor = item.actor,
                    sourceChannel = item.source_channel,
                )
            } else {
                activityLogRepository.upsertEpisodicEvent(
                    label = label,
                    rawPhrase = item.raw_phrase?.trim()?.takeIf { it.isNotBlank() },
                    source = ActivitySource.EXTRACTOR,
                    eventKind = eventKind,
                    confidence = confidence,
                    scheduledAtMs = scheduledAtMs,
                    scheduledDayKey = scheduledDayKey,
                    actor = item.actor,
                    sourceChannel = item.source_channel,
                )
            }
            if (id >= 0L) saved++
        }
        return saved
    }

    private fun parsePayload(raw: String): EpisodicExtractionPayload? {
        val json = MemoryExtractionService.extractJsonBody(raw)
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    private fun parseKind(raw: String?): EpisodeKind {
        return when (raw?.trim()?.lowercase()) {
            "plan" -> EpisodeKind.PLAN
            "social_thread" -> EpisodeKind.SOCIAL_THREAD
            "commitment" -> EpisodeKind.COMMITMENT
            else -> EpisodeKind.PHYSICAL_NOW
        }
    }

    private fun parseConfidence(raw: String?): EpisodeConfidence {
        return when (raw?.trim()?.lowercase()) {
            "confirmed" -> EpisodeConfidence.CONFIRMED
            else -> EpisodeConfidence.TENTATIVE
        }
    }

    companion object {
        private const val TAG = "EpisodicExtraction"
    }
}
