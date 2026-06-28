package com.example.mydeskrobot.data.heartbeat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sensingLogDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sensing_log",
)

enum class SensingKind {
    PRESENCE_ML,
    PRESENCE_LLM,
    ROOM_SCENE,
    LOOK_AROUND,
}

class SensingLogRepository(
    private val context: Context,
) {
    suspend fun record(kind: SensingKind, outcome: String? = null) {
        val key = keyFor(kind)
        context.sensingLogDataStore.edit { prefs ->
            prefs[key] = System.currentTimeMillis()
            outcome?.let { prefs[outcomeKey(kind)] = System.currentTimeMillis() }
        }
    }

    suspend fun lastAt(kind: SensingKind): Long? {
        val prefs = context.sensingLogDataStore.data.first()
        return prefs[keyFor(kind)]
    }

    private fun keyFor(kind: SensingKind) = when (kind) {
        SensingKind.PRESENCE_ML -> KEY_PRESENCE_ML
        SensingKind.PRESENCE_LLM -> KEY_PRESENCE_LLM
        SensingKind.ROOM_SCENE -> KEY_ROOM_SCENE
        SensingKind.LOOK_AROUND -> KEY_LOOK_AROUND
    }

    private fun outcomeKey(kind: SensingKind) = when (kind) {
        SensingKind.PRESENCE_ML -> KEY_PRESENCE_ML_OUTCOME
        SensingKind.PRESENCE_LLM -> KEY_PRESENCE_LLM_OUTCOME
        else -> keyFor(kind)
    }

    companion object {
        private val KEY_PRESENCE_ML = longPreferencesKey("last_presence_ml")
        private val KEY_PRESENCE_LLM = longPreferencesKey("last_presence_llm")
        private val KEY_ROOM_SCENE = longPreferencesKey("last_room_scene")
        private val KEY_LOOK_AROUND = longPreferencesKey("last_look_around")
        private val KEY_PRESENCE_ML_OUTCOME = longPreferencesKey("last_presence_ml_outcome")
        private val KEY_PRESENCE_LLM_OUTCOME = longPreferencesKey("last_presence_llm_outcome")
    }
}
