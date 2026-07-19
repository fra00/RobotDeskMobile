package com.example.mydeskrobot.data.predictivity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.predictivityMiningDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "predictivity_mining_v1",
)

class PredictivityMiningRepository(
    private val context: Context,
) : PredictivityMiningStore {
    override suspend fun getLastMinedDayKey(): String? =
        context.predictivityMiningDataStore.data
            .map { it[KEY_LAST_MINED_DAY] }
            .first()

    override suspend fun setLastMinedDayKey(dayKey: String) {
        context.predictivityMiningDataStore.edit { prefs ->
            prefs[KEY_LAST_MINED_DAY] = dayKey
        }
    }

    companion object {
        private val KEY_LAST_MINED_DAY = stringPreferencesKey("last_mined_day_key")
    }
}
