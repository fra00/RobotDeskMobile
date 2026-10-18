package com.example.mydeskrobot.domain.wellness

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses the optional `wellness` object from an LLM JSON response into [WellnessSnapshot].
 */
object WellnessSnapshotParser {

    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    fun fromLlmWellnessObject(
        wellnessJson: JSONObject?,
        computedAtMs: Long = System.currentTimeMillis(),
        visualOrderDone: Boolean = false,
    ): WellnessSnapshot? {
        if (wellnessJson == null) return null
        val domainsArr = wellnessJson.optJSONArray("domains") ?: return null
        val domains = parseDomainArray(domainsArr)
        if (domains.isEmpty()) return null

        val dayKey = wellnessJson.optString("day_key").trim()
            .ifBlank { wellnessJson.optString("dayKey").trim() }
            .ifBlank { dayKeyFormat.format(Date(computedAtMs)) }

        val notifySuggested = when {
            wellnessJson.has("notify_suggested") -> wellnessJson.optBoolean("notify_suggested")
            wellnessJson.has("notifySuggested") -> wellnessJson.optBoolean("notifySuggested")
            else -> domains.any { it.status.needsAttention }
        }
        val visual = when {
            wellnessJson.has("visual_order_done") -> wellnessJson.optBoolean("visual_order_done")
            wellnessJson.has("visualOrderDone") -> wellnessJson.optBoolean("visualOrderDone")
            else -> visualOrderDone
        }

        return WellnessSnapshot(
            computedAtMs = computedAtMs,
            dayKey = dayKey,
            domains = domains,
            visualOrderDone = visual,
            notifySuggested = notifySuggested,
        )
    }

    fun fromFullLlmJson(
        rawJson: String,
        computedAtMs: Long = System.currentTimeMillis(),
        visualOrderDone: Boolean = false,
    ): WellnessSnapshot? {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        val wellness = root.optJSONObject("wellness") ?: return null
        return fromLlmWellnessObject(wellness, computedAtMs, visualOrderDone)
    }

    fun toStorageJson(snapshot: WellnessSnapshot): String {
        val root = JSONObject()
        root.put("computedAtMs", snapshot.computedAtMs)
        root.put("dayKey", snapshot.dayKey)
        root.put("visualOrderDone", snapshot.visualOrderDone)
        root.put("notifySuggested", snapshot.notifySuggested)
        val arr = JSONArray()
        snapshot.domains.forEach { entry ->
            arr.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("status", entry.status.name.lowercase())
                    .put("summaryIt", entry.summaryIt),
            )
        }
        root.put("domains", arr)
        return root.toString()
    }

    fun fromStorageJson(raw: String): WellnessSnapshot? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val domainsArr = root.optJSONArray("domains") ?: return null
        val domains = parseDomainArray(domainsArr)
        if (domains.isEmpty()) return null
        return WellnessSnapshot(
            computedAtMs = root.optLong("computedAtMs", System.currentTimeMillis()),
            dayKey = root.optString("dayKey").ifBlank { dayKeyFormat.format(Date()) },
            domains = domains,
            visualOrderDone = root.optBoolean("visualOrderDone", false),
            notifySuggested = root.optBoolean(
                "notifySuggested",
                domains.any { it.status.needsAttention },
            ),
        )
    }

    private fun parseDomainArray(domainsArr: JSONArray): List<WellnessDomainEntry> {
        val domains = mutableListOf<WellnessDomainEntry>()
        for (i in 0 until domainsArr.length()) {
            val obj = domainsArr.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isBlank()) continue
            val status = WellnessDomainStatus.fromLlm(obj.optString("status")) ?: continue
            val summary = obj.optString("summary_it")
                .ifBlank { obj.optString("summaryIt") }
                .trim()
            if (summary.isBlank()) continue
            domains.add(WellnessDomainEntry(id = id, status = status, summaryIt = summary))
        }
        return domains
    }
}
