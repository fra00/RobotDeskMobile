package com.example.mydeskrobot.data.heartbeat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.heartbeat.AttentionDomain
import com.example.mydeskrobot.domain.heartbeat.AttentionDomainState
import com.example.mydeskrobot.domain.heartbeat.DomainSensitivity
import com.example.mydeskrobot.domain.heartbeat.DomainTrigger
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private val Context.attentionDomainsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "attention_domains_v1",
)

class AttentionDomainRepository(
    private val context: Context,
) {
    val builtInCatalog: List<AttentionDomain> = listOf(
        AttentionDomain(
            id = "pasti",
            displayName = "Pasti",
            promptAsset = "prompts/domains/pasti.txt",
            trigger = DomainTrigger.TimeDaily(hour = 12, endHourExclusive = 14),
            sensitivity = DomainSensitivity.MEDIUM,
            requiresPresenceCheck = true,
        ),
        AttentionDomain(
            id = "attivita_fisica",
            displayName = "Attività fisica",
            promptAsset = "prompts/domains/attivita_fisica.txt",
            trigger = DomainTrigger.TimeDaily(hour = 20),
            sensitivity = DomainSensitivity.MEDIUM,
        ),
        AttentionDomain(
            id = "carico_lavoro",
            displayName = "Carico di lavoro",
            promptAsset = "prompts/domains/carico_lavoro.txt",
            trigger = DomainTrigger.TimeDaily(hour = 19),
            sensitivity = DomainSensitivity.HIGH,
            requiresPresenceCheck = true,
        ),
        AttentionDomain(
            id = "contatti_sociali",
            displayName = "Contatti sociali",
            promptAsset = "prompts/domains/contatti_sociali.txt",
            trigger = DomainTrigger.TimeWeekly(dayOfWeek = Calendar.MONDAY),
            sensitivity = DomainSensitivity.HIGH,
        ),
        AttentionDomain(
            id = "ordine_ambiente",
            displayName = "Ordine ambiente",
            promptAsset = "prompts/domains/ordine_ambiente.txt",
            trigger = DomainTrigger.Event(eventId = "nuova_foto"),
            sensitivity = DomainSensitivity.LOW,
            canUseCamera = true,
        ),
        AttentionDomain(
            id = "spatial",
            displayName = "Stanze",
            promptAsset = "prompts/domains/spatial.txt",
            trigger = DomainTrigger.Event(eventId = "cambio_stanza"),
            sensitivity = DomainSensitivity.LOW,
            canUseCamera = true,
        ),
    )

    suspend fun listStates(): List<AttentionDomainState> {
        val saved = loadPersisted()
        val savedById = saved.associateBy { it.id }
        val builtInStates = builtInCatalog.map { domain ->
            val persisted = savedById[domain.id]
            domain.toState(
                enabled = persisted?.enabled ?: true,
                lastCheckedAt = persisted?.lastCheckedAt,
                userPrompt = persisted?.userPrompt,
            )
        }
        val custom = saved.filter { !it.isBuiltIn }.map { it.toState() }
        return builtInStates + custom
    }

    suspend fun enabledDomains(): List<AttentionDomainState> =
        listStates().filter { it.enabled }

    suspend fun saveStates(states: List<AttentionDomainState>) {
        val array = JSONArray()
        states.forEach { array.put(it.toJson()) }
        context.attentionDomainsDataStore.edit {
            it[KEY_JSON] = array.toString()
        }
    }

    suspend fun updateLastChecked(domainId: String, timestamp: Long) {
        val states = listStates().map { state ->
            if (state.id == domainId) state.copy(lastCheckedAt = timestamp) else state
        }
        saveStates(states)
    }

    private suspend fun loadPersisted(): List<PersistedDomain> {
        val raw = context.attentionDomainsDataStore.data.first()[KEY_JSON] ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    add(PersistedDomain.fromJson(array.getJSONObject(i)))
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private val KEY_JSON = stringPreferencesKey("attention_domains_json")
    }
}

private data class PersistedDomain(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val userPrompt: String?,
    val triggerType: String,
    val triggerHour: Int,
    val triggerEndHour: Int?,
    val triggerDayOfWeek: Int,
    val triggerEventId: String?,
    val sensitivity: String,
    val lastCheckedAt: Long?,
    val requiresPresenceCheck: Boolean,
    val canUseCamera: Boolean,
) {
    fun toState(): AttentionDomainState {
        val trigger = when (triggerType) {
            "TIME_DAILY" -> DomainTrigger.TimeDaily(
                hour = triggerHour,
                endHourExclusive = triggerEndHour,
            )
            "TIME_WEEKLY" -> DomainTrigger.TimeWeekly(dayOfWeek = triggerDayOfWeek)
            "EVENT" -> DomainTrigger.Event(eventId = triggerEventId.orEmpty())
            else -> DomainTrigger.TimeDaily(hour = triggerHour)
        }
        val sensitivityEnum = runCatching {
            DomainSensitivity.valueOf(sensitivity)
        }.getOrDefault(DomainSensitivity.MEDIUM)

        return AttentionDomainState(
            id = id,
            displayName = displayName,
            enabled = enabled,
            isBuiltIn = isBuiltIn,
            userPrompt = userPrompt,
            trigger = trigger,
            sensitivity = sensitivityEnum,
            lastCheckedAt = lastCheckedAt,
            requiresPresenceCheck = requiresPresenceCheck,
            canUseCamera = canUseCamera,
        )
    }

    companion object {
        fun fromJson(obj: JSONObject): PersistedDomain = PersistedDomain(
            id = obj.getString("id"),
            displayName = obj.getString("displayName"),
            enabled = obj.optBoolean("enabled", true),
            isBuiltIn = obj.optBoolean("isBuiltIn", false),
            userPrompt = obj.optString("userPrompt").takeIf { it.isNotBlank() },
            triggerType = obj.optString("triggerType", "TIME_DAILY"),
            triggerHour = obj.optInt("triggerHour", 0),
            triggerEndHour = obj.optInt("triggerEndHour", -1).takeIf { it >= 0 },
            triggerDayOfWeek = obj.optInt("triggerDayOfWeek", Calendar.MONDAY),
            triggerEventId = obj.optString("triggerEventId").takeIf { it.isNotBlank() },
            sensitivity = obj.optString("sensitivity", "MEDIUM"),
            lastCheckedAt = obj.optLong("lastCheckedAt").takeIf { it > 0L },
            requiresPresenceCheck = obj.optBoolean("requiresPresenceCheck", false),
            canUseCamera = obj.optBoolean("canUseCamera", false),
        )
    }
}

private fun AttentionDomain.toState(
    enabled: Boolean,
    lastCheckedAt: Long?,
    userPrompt: String?,
): AttentionDomainState = AttentionDomainState(
    id = id,
    displayName = displayName,
    enabled = enabled,
    isBuiltIn = isBuiltIn,
    userPrompt = userPrompt,
    trigger = trigger,
    sensitivity = sensitivity,
    lastCheckedAt = lastCheckedAt,
    requiresPresenceCheck = requiresPresenceCheck,
    canUseCamera = canUseCamera,
)

private fun AttentionDomainState.toJson(): JSONObject {
    val obj = JSONObject()
    obj.put("id", id)
    obj.put("displayName", displayName)
    obj.put("enabled", enabled)
    obj.put("isBuiltIn", isBuiltIn)
    userPrompt?.let { obj.put("userPrompt", it) }
    when (val t = trigger) {
        is DomainTrigger.TimeDaily -> {
            obj.put("triggerType", "TIME_DAILY")
            obj.put("triggerHour", t.hour)
            t.endHourExclusive?.let { obj.put("triggerEndHour", it) }
        }
        is DomainTrigger.TimeWeekly -> {
            obj.put("triggerType", "TIME_WEEKLY")
            obj.put("triggerDayOfWeek", t.dayOfWeek)
        }
        is DomainTrigger.Event -> {
            obj.put("triggerType", "EVENT")
            obj.put("triggerEventId", t.eventId)
        }
    }
    obj.put("sensitivity", sensitivity.name)
    lastCheckedAt?.let { obj.put("lastCheckedAt", it) }
    obj.put("requiresPresenceCheck", requiresPresenceCheck)
    obj.put("canUseCamera", canUseCamera)
    return obj
}
