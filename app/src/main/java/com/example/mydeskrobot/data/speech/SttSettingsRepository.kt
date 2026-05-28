package com.example.mydeskrobot.data.speech

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.speech.SttProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sttDataStore by preferencesDataStore(name = "stt_settings")

class SttSettingsRepository(private val context: Context) {

    companion object {
        private val KEY_STT_PROVIDER = stringPreferencesKey("stt_provider")
    }

    suspend fun getProvider(): SttProvider {
        return context.sttDataStore.data.map { prefs ->
            val value = prefs[KEY_STT_PROVIDER]
            when (value) {
                "VOSK" -> SttProvider.VOSK
                "ANDROID" -> SttProvider.ANDROID
                else -> SttProvider.ANDROID // default
            }
        }.first()
    }

    suspend fun setProvider(provider: SttProvider) {
        context.sttDataStore.edit { prefs ->
            prefs[KEY_STT_PROVIDER] = provider.name
        }
    }
}
