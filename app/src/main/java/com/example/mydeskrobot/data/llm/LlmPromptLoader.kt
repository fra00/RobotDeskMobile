package com.example.mydeskrobot.data.llm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Carica i prompt LLM da file in [assets/prompts/].
 */
object LlmPromptLoader {

    const val SYSTEM_PROMPT_ASSET_PATH = "prompts/llm_system_prompt.txt"
    const val MEMORY_EXTRACTOR_PROMPT_ASSET_PATH = "prompts/memory_extractor_prompt.txt"
    const val PRESENCE_DETECTION_PROMPT_ASSET_PATH = "prompts/presence_detection_prompt.txt"
    const val ROOM_LANDMARKS_PROMPT_ASSET_PATH = "prompts/room_landmarks_prompt.txt"
    const val BODY_CAPABILITIES_PROMPT_ASSET_PATH = "prompts/body_capabilities_prompt.txt"
    const val HEARTBEAT_PLAYBOOK_PROMPT_ASSET_PATH = "prompts/heartbeat_playbook_prompt.txt"
    const val MEMORY_CONSOLIDATION_PROMPT_ASSET_PATH = "prompts/memory_consolidation_prompt.txt"
    const val MEMORY_RECALL_PLANNER_PROMPT_ASSET_PATH = "prompts/memory_recall_planner_prompt.txt"
    const val EPISODIC_EXTRACTOR_PROMPT_ASSET_PATH = "prompts/episodic_extractor_prompt.txt"
    const val ACTIVITY_EXTRACTOR_PROMPT_ASSET_PATH = EPISODIC_EXTRACTOR_PROMPT_ASSET_PATH
    const val ACTIVITY_HABIT_SUMMARY_PROMPT_ASSET_PATH = "prompts/activity_habit_summary_prompt.txt"
    const val HABIT_LABEL_NORMALIZE_PROMPT_ASSET_PATH = "prompts/habit_label_normalize_prompt.txt"
    const val PREDICTIVITY_DEVIATION_PROMPT_ASSET_PATH = "prompts/predictivity_deviation_prompt.txt"
    const val WELLNESS_CHECK_PROMPT_ASSET_PATH = "prompts/wellness_check_prompt.txt"
    const val ROOM_ORDER_AUDIT_PROMPT_ASSET_PATH = "prompts/room_order_audit_prompt.txt"

    private const val DATETIME_PLACEHOLDER = "{{CURRENT_DATETIME}}"

    fun loadSystemPrompt(context: Context): String {
        val text = loadTextAsset(context, SYSTEM_PROMPT_ASSET_PATH)

        require(text.isNotBlank()) {
            "System prompt asset is empty: $SYSTEM_PROMPT_ASSET_PATH"
        }

        return text
    }

    fun loadMemoryExtractorPrompt(context: Context): String {
        val text = loadTextAsset(context, MEMORY_EXTRACTOR_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Memory extractor prompt asset is empty: $MEMORY_EXTRACTOR_PROMPT_ASSET_PATH"
        }
        return text.replace(DATETIME_PLACEHOLDER, getCurrentDateTimeString())
    }

    fun loadMemoryConsolidationPrompt(context: Context): String {
        val text = loadTextAsset(context, MEMORY_CONSOLIDATION_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Memory consolidation prompt asset is empty: $MEMORY_CONSOLIDATION_PROMPT_ASSET_PATH"
        }
        return text.replace(DATETIME_PLACEHOLDER, getCurrentDateTimeString())
    }

    fun loadMemoryRecallPlannerPrompt(context: Context): String {
        val text = loadTextAsset(context, MEMORY_RECALL_PLANNER_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Memory recall planner prompt asset is empty: $MEMORY_RECALL_PLANNER_PROMPT_ASSET_PATH"
        }
        return text.replace(DATETIME_PLACEHOLDER, getCurrentDateTimeString())
    }

    private fun getCurrentDateTimeString(): String {
        val dateFormat = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN)
        return dateFormat.format(Date())
    }

    fun loadBodyCapabilitiesPrompt(context: Context): String {
        val text = loadTextAsset(context, BODY_CAPABILITIES_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Body capabilities prompt asset is empty: $BODY_CAPABILITIES_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadHeartbeatPlaybookPrompt(context: Context): String {
        val text = loadTextAsset(context, HEARTBEAT_PLAYBOOK_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Heartbeat playbook prompt asset is empty: $HEARTBEAT_PLAYBOOK_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadActivityExtractorPrompt(context: Context): String {
        val text = loadTextAsset(context, ACTIVITY_EXTRACTOR_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Activity extractor prompt asset is empty: $ACTIVITY_EXTRACTOR_PROMPT_ASSET_PATH"
        }
        return text.replace(DATETIME_PLACEHOLDER, getCurrentDateTimeString())
    }

    fun loadActivityHabitSummaryPrompt(context: Context): String {
        val text = loadTextAsset(context, ACTIVITY_HABIT_SUMMARY_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Activity habit summary prompt asset is empty: $ACTIVITY_HABIT_SUMMARY_PROMPT_ASSET_PATH"
        }
        return text.replace(DATETIME_PLACEHOLDER, getCurrentDateTimeString())
    }

    fun loadHabitLabelNormalizePrompt(context: Context): String {
        val text = loadTextAsset(context, HABIT_LABEL_NORMALIZE_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Habit label normalize prompt asset is empty: $HABIT_LABEL_NORMALIZE_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadPredictivityDeviationPrompt(context: Context): String {
        val text = loadTextAsset(context, PREDICTIVITY_DEVIATION_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Predictivity deviation prompt asset is empty: $PREDICTIVITY_DEVIATION_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadWellnessCheckPrompt(context: Context): String {
        val text = loadTextAsset(context, WELLNESS_CHECK_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Wellness check prompt asset is empty: $WELLNESS_CHECK_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadRoomOrderAuditPrompt(context: Context): String {
        val text = loadTextAsset(context, ROOM_ORDER_AUDIT_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Room order audit prompt asset is empty: $ROOM_ORDER_AUDIT_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadPresenceDetectionPrompt(context: Context): String {
        val text = loadTextAsset(context, PRESENCE_DETECTION_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Presence detection prompt asset is empty: $PRESENCE_DETECTION_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadRoomLandmarksPrompt(context: Context): String {
        val text = loadTextAsset(context, ROOM_LANDMARKS_PROMPT_ASSET_PATH)
        require(text.isNotBlank()) {
            "Room landmarks prompt asset is empty: $ROOM_LANDMARKS_PROMPT_ASSET_PATH"
        }
        return text
    }

    fun loadOptionalAsset(context: Context, path: String): String =
        runCatching { loadTextAsset(context, path) }.getOrDefault("")

    private fun loadTextAsset(context: Context, path: String): String =
        context.assets.open(path).use { input ->
            input.bufferedReader().readText()
        }.trim()
}
