package com.example.mydeskrobot.data.llm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.mydeskrobot.BuildConfig
import com.example.mydeskrobot.R
import com.example.mydeskrobot.domain.llm.LlmProvider
import com.example.mydeskrobot.domain.llm.LlmSettings
import com.example.mydeskrobot.domain.llm.LlmSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.llmDataStore: DataStore<Preferences> by preferencesDataStore(name = "llm_settings")

class LlmSettingsRepositoryImpl(
    private val context: Context,
) : LlmSettingsRepository {

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_ENCRYPTED_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override val settings: Flow<LlmSettings> =
        context.llmDataStore.data.map { prefs -> prefs.toSettings() }

    override suspend fun load(): LlmSettings {
        val prefs = context.llmDataStore.data.first()
        if (prefs[KEY_PROVIDER] == null) {
            val seed = seedFromBuildConfig()
            save(seed)
            return seed
        }
        return prefs.toSettings()
    }

    override suspend fun save(settings: LlmSettings) {
        context.llmDataStore.edit { prefs ->
            prefs[KEY_PROVIDER] = settings.provider.name
            prefs[KEY_BASE_URL] = settings.baseUrl.trim()
            prefs[KEY_TEXT_MODEL] = settings.textModel.trim()
            prefs[KEY_VISION_MODEL] = settings.visionModel.trim()
        }
        encryptedPrefs.edit()
            .putString(KEY_API_KEY, settings.apiKey)
            .apply()
    }

    override fun isValid(settings: LlmSettings): Boolean = validationError(settings) == null

    override fun validationError(settings: LlmSettings): String? {
        return when (settings.provider) {
            LlmProvider.LM_STUDIO -> {
                when {
                    settings.baseUrl.isBlank() ->
                        context.getString(R.string.llm_error_base_url_required)
                    settings.textModel.isBlank() ->
                        context.getString(R.string.llm_error_text_model_required)
                    else -> null
                }
            }
            LlmProvider.GEMINI -> {
                when {
                    settings.apiKey.isBlank() ->
                        context.getString(R.string.llm_error_api_key_required)
                    settings.textModel.isBlank() ->
                        context.getString(R.string.llm_error_text_model_required)
                    else -> null
                }
            }
        }
    }

    private fun Preferences.toSettings(): LlmSettings {
        val providerName = this[KEY_PROVIDER] ?: LlmProvider.LM_STUDIO.name
        val provider = runCatching { LlmProvider.valueOf(providerName) }
            .getOrDefault(LlmProvider.LM_STUDIO)
        return LlmSettings(
            provider = provider,
            baseUrl = this[KEY_BASE_URL].orEmpty(),
            textModel = this[KEY_TEXT_MODEL].orEmpty(),
            visionModel = this[KEY_VISION_MODEL].orEmpty(),
            apiKey = encryptedPrefs.getString(KEY_API_KEY, "").orEmpty(),
        )
    }

    private fun seedFromBuildConfig(): LlmSettings {
        return LlmSettings(
            provider = LlmProvider.LM_STUDIO,
            baseUrl = BuildConfig.LLM_BASE_URL,
            textModel = BuildConfig.LLM_MODEL,
            visionModel = BuildConfig.LLM_VISION_MODEL,
            apiKey = BuildConfig.LLM_API_KEY,
        )
    }

    companion object {
        private const val PREFS_ENCRYPTED_NAME = "llm_secure_prefs"
        private val KEY_PROVIDER = stringPreferencesKey("provider")
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_TEXT_MODEL = stringPreferencesKey("text_model")
        private val KEY_VISION_MODEL = stringPreferencesKey("vision_model")
        private const val KEY_API_KEY = "api_key"

        fun create(context: Context): LlmSettingsRepository =
            LlmSettingsRepositoryImpl(context.applicationContext)
    }
}
