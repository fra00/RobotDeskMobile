package com.example.mydeskrobot.data.translation

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.translation.TranslationModeCommands
import com.example.mydeskrobot.reasoning.model.TranslationModeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.translationModeDataStore by preferencesDataStore(name = "translation_mode")

class TranslationModeRepository(
    context: Context,
) {
    private val dataStore = context.applicationContext.translationModeDataStore

    companion object {
        private const val TAG = "TranslationModeRepo"
        private val KEY_ENABLED = booleanPreferencesKey("enabled")
        private val KEY_LANGUAGE_TAG = stringPreferencesKey("target_language_tag")
        private val KEY_LANGUAGE_LABEL = stringPreferencesKey("target_language_label")
    }

    suspend fun getState(): TranslationModeState =
        dataStore.data.map { prefs -> readState(prefs) }.first()

    fun observeState(): Flow<TranslationModeState> =
        dataStore.data
            .map { prefs -> readState(prefs) }
            .distinctUntilChanged()

    suspend fun setState(state: TranslationModeState) {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = state.enabled
            if (state.enabled) {
                prefs[KEY_LANGUAGE_TAG] = state.targetLanguageTag
                prefs[KEY_LANGUAGE_LABEL] = state.targetLanguageLabel
            } else {
                prefs.remove(KEY_LANGUAGE_TAG)
                prefs.remove(KEY_LANGUAGE_LABEL)
            }
        }
        Log.i(
            TAG,
            if (state.enabled) {
                "Translation mode ON → ${state.targetLanguageLabel} (${state.targetLanguageTag})"
            } else {
                "Translation mode OFF"
            },
        )
    }

    suspend fun clear() {
        setState(TranslationModeState.OFF)
    }

    /**
     * Applies tool params. Returns the new state or throws [IllegalArgumentException].
     */
    suspend fun applyFromToolParams(params: Map<String, Any?>): TranslationModeState {
        val state = TranslationModeCommands.parse(params)
        if (!state.enabled) {
            clear()
            return TranslationModeState.OFF
        }
        setState(state)
        return state
    }

    private fun readState(prefs: Preferences): TranslationModeState {
        val enabled = prefs[KEY_ENABLED] ?: false
        if (!enabled) return TranslationModeState.OFF
        return TranslationModeState(
            enabled = true,
            targetLanguageTag = prefs[KEY_LANGUAGE_TAG].orEmpty(),
            targetLanguageLabel = prefs[KEY_LANGUAGE_LABEL].orEmpty(),
        )
    }
}
