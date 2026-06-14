package com.example.mydeskrobot.activity.extract

import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.domain.activitylog.ActivitySource
import com.example.mydeskrobot.integration.llm.LlmHttpErrors
import com.example.mydeskrobot.memory.extract.ChatLogEntry
import com.example.mydeskrobot.memory.extract.MemoryExtractionService
import com.example.mydeskrobot.reasoning.llm.LlmClient
import com.example.mydeskrobot.reasoning.model.ConversationMessage
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal data class ActivityExtractionPayload(
    val activities: List<ActivityItemPayload> = emptyList(),
)

internal data class ActivityItemPayload(
    val label: String? = null,
    val raw_phrase: String? = null,
)

class ActivityExtractionService(
    private val llmClient: LlmClient,
    private val activityLogRepository: ActivityLogRepository,
    private val extractorPrompt: String,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ActivityExtractionPayload::class.java)

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
            Log.w(TAG, "Activity extractor LLM failed: ${LlmHttpErrors.formatForLog(error)}", error)
            return 0
        }
        val raw = llmResult.getOrNull()?.content?.trim().orEmpty()
        if (raw.isBlank()) {
            Log.w(TAG, "Activity extractor returned empty content")
            return 0
        }

        val payload = parsePayload(raw)
        if (payload == null) {
            Log.w(TAG, "Activity extractor JSON parse failed: ${raw.take(120)}")
            return 0
        }
        if (payload.activities.isEmpty()) {
            Log.d(TAG, "Activity extractor returned no activities")
            return 0
        }

        var saved = 0
        payload.activities.forEach { item ->
            val label = item.label?.trim().orEmpty()
            if (label.isBlank()) return@forEach
            val id = activityLogRepository.appendEvent(
                label = label,
                rawPhrase = item.raw_phrase?.trim()?.takeIf { it.isNotBlank() },
                source = ActivitySource.EXTRACTOR,
            )
            if (id >= 0L) saved++
        }
        return saved
    }

    private fun parsePayload(raw: String): ActivityExtractionPayload? {
        val json = MemoryExtractionService.extractJsonBody(raw)
        return runCatching { adapter.fromJson(json) }.getOrNull()
    }

    companion object {
        private const val TAG = "ActivityExtraction"
    }
}
