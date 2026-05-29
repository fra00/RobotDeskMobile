package com.example.mydeskrobot.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.BuildConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.searchSettingsDataStore by preferencesDataStore(name = "search_settings")

/**
 * SearXNG base URL for [com.example.mydeskrobot.integration.tool.remote.WebSearchTool].
 * Override via DataStore (future UI) or SEARX_BASE_URL in local.properties.
 */
class SearchSettingsRepository(
    private val context: Context,
) {
    private val dataStore = context.searchSettingsDataStore

    suspend fun getSearxBaseUrl(): String = getSearxBaseUrls().first()

    suspend fun getSearxBaseUrls(): List<String> {
        val stored = dataStore.data.map { prefs ->
            prefs[KEY_SEARX_BASE_URL]?.trim().orEmpty()
        }.first()
        val urls = mutableListOf<String>()
        if (stored.isNotBlank()) {
            urls.add(normalizeBaseUrl(stored))
        } else {
            val fromBuild = BuildConfig.SEARX_BASE_URL.trim()
            if (fromBuild.isNotBlank()) {
                urls.add(normalizeBaseUrl(fromBuild))
            }
        }
        urls.addAll(BUILTIN_FALLBACK_INSTANCES.map { normalizeBaseUrl(it) })
        return urls.distinct()
    }

    suspend fun setSearxBaseUrl(url: String) {
        dataStore.edit { prefs ->
            val normalized = url.trim()
            if (normalized.isEmpty()) {
                prefs.remove(KEY_SEARX_BASE_URL)
            } else {
                prefs[KEY_SEARX_BASE_URL] = normalizeBaseUrl(normalized)
            }
        }
    }

    companion object {
        private val KEY_SEARX_BASE_URL = stringPreferencesKey("searx_base_url")

        /** Legacy default; public instances often return 403 to bots — see DuckDuckGo fallback. */
        const val DEFAULT_SEARX_BASE_URL = "https://searx.be"

        /** Extra SearXNG instances tried if the primary fails. */
        val BUILTIN_FALLBACK_INSTANCES = listOf(
            "https://searx.tiekoetter.com",
            "https://search.sapti.me",
        )

        fun normalizeBaseUrl(url: String): String {
            var base = url.trim().removeSuffix("/")
            if (!base.startsWith("http://") && !base.startsWith("https://")) {
                base = "https://$base"
            }
            return base
        }
    }
}
