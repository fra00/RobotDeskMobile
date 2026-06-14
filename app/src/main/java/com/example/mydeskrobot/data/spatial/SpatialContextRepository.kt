package com.example.mydeskrobot.data.spatial

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import com.example.mydeskrobot.domain.spatial.SpatialJsonArrays
import com.example.mydeskrobot.domain.spatial.SpatialResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.spatialContextDataStore by preferencesDataStore(name = "spatial_context")

class SpatialContextRepository(
    private val context: Context,
) {
    private val dataStore = context.spatialContextDataStore

    suspend fun load(): SpatialContextSnapshot {
        val prefs = dataStore.data.first()
        return readSnapshot(prefs)
    }

    fun observe(): Flow<SpatialContextSnapshot> =
        dataStore.data.map { prefs -> readSnapshot(prefs) }

    suspend fun save(snapshot: SpatialContextSnapshot) {
        dataStore.edit { prefs ->
            if (snapshot.currentPlaceId != null) {
                prefs[KEY_PLACE_ID] = snapshot.currentPlaceId
            } else {
                prefs.remove(KEY_PLACE_ID)
            }
            snapshot.currentPlaceLabel?.let { prefs[KEY_PLACE_LABEL] = it }
                ?: prefs.remove(KEY_PLACE_LABEL)
            snapshot.roomType?.let { prefs[KEY_ROOM_TYPE] = it.name }
                ?: prefs.remove(KEY_ROOM_TYPE)
            prefs[KEY_CONFIDENCE] = snapshot.confidence.coerceIn(0f, 1f)
            prefs[KEY_RESOLUTION] = snapshot.resolution.name
            if (snapshot.lastLandmarks.isNotEmpty()) {
                prefs[KEY_LANDMARKS] = encodeLandmarks(snapshot.lastLandmarks)
            } else {
                prefs.remove(KEY_LANDMARKS)
            }
        }
    }

    suspend fun invalidate() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PLACE_ID)
            prefs.remove(KEY_PLACE_LABEL)
            prefs.remove(KEY_ROOM_TYPE)
            prefs[KEY_CONFIDENCE] = 0f
            prefs[KEY_RESOLUTION] = SpatialResolution.UNKNOWN.name
            prefs.remove(KEY_LANDMARKS)
        }
    }

    private fun readSnapshot(prefs: androidx.datastore.preferences.core.Preferences): SpatialContextSnapshot {
        val resolution = prefs[KEY_RESOLUTION]?.let { raw ->
            SpatialResolution.entries.find { it.name == raw }
        } ?: SpatialResolution.UNKNOWN
        val roomTypeRaw = prefs[KEY_ROOM_TYPE]
        return SpatialContextSnapshot(
            currentPlaceId = prefs[KEY_PLACE_ID],
            currentPlaceLabel = prefs[KEY_PLACE_LABEL],
            roomType = roomTypeRaw?.let { com.example.mydeskrobot.domain.spatial.RoomType.fromRaw(it) },
            confidence = prefs[KEY_CONFIDENCE] ?: 0f,
            resolution = resolution,
            lastLandmarks = decodeLandmarks(prefs[KEY_LANDMARKS].orEmpty()),
        )
    }

    companion object {
        private val KEY_PLACE_ID = longPreferencesKey("current_place_id")
        private val KEY_PLACE_LABEL = stringPreferencesKey("current_place_label")
        private val KEY_ROOM_TYPE = stringPreferencesKey("room_type")
        private val KEY_CONFIDENCE = floatPreferencesKey("confidence")
        private val KEY_RESOLUTION = stringPreferencesKey("resolution")
        private val KEY_LANDMARKS = stringPreferencesKey("last_landmarks_json")

        private fun encodeLandmarks(items: List<String>): String = SpatialJsonArrays.encode(items)

        private fun decodeLandmarks(json: String): List<String> = SpatialJsonArrays.decode(json)
    }
}
