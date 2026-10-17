package com.example.mydeskrobot.integration.proxy.capability

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.health.HealthConnectExerciseSession
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.data.proxy.WifiNetworkHelper
import com.example.mydeskrobot.domain.proxy.PairedProxyDevice
import com.example.mydeskrobot.integration.telephony.ContactPhoneMatch
import com.example.mydeskrobot.integration.telephony.ContactPhoneSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * ROBOT → PROXY capability HTTP client.
 */
class PhoneCapabilityClient(
    private val appContext: Context,
    private val modeRepository: DeviceModeRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        private const val TAG = "PhoneCapClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun ping(proxy: PairedProxyDevice? = null): Boolean = withContext(Dispatchers.IO) {
        val target = proxy ?: modeRepository.primaryCapabilityProxy() ?: return@withContext false
        getJson(target, PhoneCapabilityId.PING.wireId, emptyMap()) != null
    }

    suspend fun fetchExercises(
        fromMs: Long,
        toMs: Long,
        proxy: PairedProxyDevice? = null,
    ): List<HealthConnectExerciseSession>? = withContext(Dispatchers.IO) {
        val target = proxy ?: modeRepository.primaryCapabilityProxy() ?: return@withContext null
        val json = getJson(
            target,
            PhoneCapabilityId.EXERCISES.wireId,
            mapOf("fromMs" to fromMs.toString(), "toMs" to toMs.toString()),
        ) ?: return@withContext null
        parseExercises(json)
    }

    suspend fun searchContacts(
        query: String,
        proxy: PairedProxyDevice? = null,
    ): List<ContactPhoneMatch>? = withContext(Dispatchers.IO) {
        val target = proxy ?: modeRepository.primaryCapabilityProxy() ?: return@withContext null
        val json = getJson(
            target,
            PhoneCapabilityId.CONTACTS.wireId,
            mapOf("q" to query),
        ) ?: return@withContext null
        parseContacts(json)
    }

    suspend fun postLocalNotification(
        title: String,
        text: String,
        notificationId: Int,
        proxy: PairedProxyDevice? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val target = proxy ?: modeRepository.primaryCapabilityProxy() ?: return@withContext false
        if (!WifiNetworkHelper.isOnWifi(appContext)) return@withContext false
        val url = "http://${target.lanIp}:${target.capabilityPort}/v1/capabilities/" +
            PhoneCapabilityId.LOCAL_NOTIFICATION.wireId
        val body = JSONObject()
            .put("title", title)
            .put("text", text)
            .put("notificationId", notificationId)
            .toString()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${target.token}")
            .post(body.toRequestBody(JSON))
            .build()
        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "local_notification OK → $url")
                    true
                } else {
                    Log.w(TAG, "local_notification HTTP ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "local_notification failed: ${e.message}")
            false
        }
    }

    /** Fan-out reminder to every paired proxy that has a LAN address. */
    suspend fun postLocalNotificationToAll(
        title: String,
        text: String,
        notificationId: Int,
    ): Int {
        var ok = 0
        for (proxy in modeRepository.listPairedProxies().filter { it.lanIp.isNotBlank() }) {
            if (postLocalNotification(title, text, notificationId, proxy)) ok++
        }
        return ok
    }

    private fun getJson(
        proxy: PairedProxyDevice,
        capabilityId: String,
        query: Map<String, String>,
    ): JSONObject? {
        if (proxy.lanIp.isBlank()) return null
        if (!WifiNetworkHelper.isOnWifi(appContext)) return null
        val base = "http://${proxy.lanIp}:${proxy.capabilityPort}/v1/capabilities/$capabilityId"
            .toHttpUrlOrNull() ?: return null
        val urlBuilder = base.newBuilder()
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer ${proxy.token}")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GET $capabilityId HTTP ${response.code}")
                    return null
                }
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw)
                if (!json.optBoolean("ok", false)) return null
                json
            }
        } catch (e: Exception) {
            Log.w(TAG, "GET $capabilityId failed: ${e.message}")
            null
        }
    }

    private fun parseExercises(json: JSONObject): List<HealthConnectExerciseSession> {
        val array = json.optJSONArray("sessions") ?: return emptyList()
        val out = mutableListOf<HealthConnectExerciseSession>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val id = o.optString("metadataId").trim()
            if (id.isEmpty()) continue
            out += HealthConnectExerciseSession(
                metadataId = id,
                exerciseType = o.optInt("exerciseType"),
                startTimeMs = o.optLong("startTimeMs"),
                endTimeMs = o.optLong("endTimeMs"),
                title = o.optString("title").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    private fun parseContacts(json: JSONObject): List<ContactPhoneMatch> {
        if (!json.optBoolean("permission", true) && json.optJSONArray("matches")?.length() == 0) {
            return emptyList()
        }
        val array = json.optJSONArray("matches") ?: return emptyList()
        val out = mutableListOf<ContactPhoneMatch>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("displayName").trim()
            val number = o.optString("number").trim()
            if (name.isEmpty() || number.isEmpty()) continue
            out += ContactPhoneMatch(
                displayName = name,
                number = number,
                source = ContactPhoneSource.CONTACTS,
                score = o.optDouble("score", 0.0).toFloat(),
            )
        }
        return out
    }
}
