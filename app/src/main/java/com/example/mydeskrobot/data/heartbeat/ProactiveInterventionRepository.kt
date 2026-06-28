package com.example.mydeskrobot.data.heartbeat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.heartbeat.InterventionOutcome
import com.example.mydeskrobot.domain.heartbeat.ProactiveIntervention
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.proactiveInterventionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "proactive_interventions",
)

class ProactiveInterventionRepository(
    private val context: Context,
) {
    suspend fun append(intervention: ProactiveIntervention) {
        val current = loadAll().toMutableList()
        current.add(intervention)
        pruneOld(current)
        saveAll(current)
    }

    suspend fun recentForDomain(domainId: String, limit: Int = 5): List<ProactiveIntervention> =
        loadAll()
            .filter { it.domainId == domainId }
            .sortedByDescending { it.timestamp }
            .take(limit)

    suspend fun recentAll(limit: Int = 20): List<ProactiveIntervention> =
        loadAll().sortedByDescending { it.timestamp }.take(limit)

    private suspend fun loadAll(): List<ProactiveIntervention> {
        val raw = context.proactiveInterventionDataStore.data.first()[KEY_JSON] ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ProactiveIntervention(
                            domainId = obj.getString("domainId"),
                            topic = obj.optString("topic", ""),
                            text = obj.optString("text", ""),
                            outcome = InterventionOutcome.valueOf(
                                obj.optString("outcome", InterventionOutcome.SILENT.name),
                            ),
                            timestamp = obj.getLong("timestamp"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun saveAll(items: List<ProactiveIntervention>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("domainId", item.domainId)
                    .put("topic", item.topic)
                    .put("text", item.text)
                    .put("outcome", item.outcome.name)
                    .put("timestamp", item.timestamp),
            )
        }
        context.proactiveInterventionDataStore.edit { it[KEY_JSON] = array.toString() }
    }

    private fun pruneOld(items: MutableList<ProactiveIntervention>) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        items.removeAll { it.timestamp < cutoff }
    }

    companion object {
        private val KEY_JSON = stringPreferencesKey("proactive_interventions_json")
        private const val RETENTION_MS = 14L * 24 * 60 * 60 * 1000
    }
}
