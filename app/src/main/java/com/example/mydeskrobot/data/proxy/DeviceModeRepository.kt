package com.example.mydeskrobot.data.proxy

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mydeskrobot.domain.proxy.DeviceMode
import com.example.mydeskrobot.domain.proxy.PairedProxyDevice
import com.example.mydeskrobot.domain.proxy.PairedRobotEndpoint
import com.example.mydeskrobot.domain.proxy.PairingQrPayload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.deviceModeDataStore by preferencesDataStore(name = "device_mode_proxy")

class DeviceModeRepository(
    private val context: Context,
) {
    private val dataStore = context.deviceModeDataStore

    companion object {
        private val KEY_MODE = stringPreferencesKey("device_mode")
        private val KEY_APPLY_RESTART = booleanPreferencesKey("apply_mode_restart")
        private val KEY_ROBOT_ID = stringPreferencesKey("robot_id")
        private val KEY_PROXY_DEVICE_ID = stringPreferencesKey("proxy_device_id")
        private val KEY_PAIRED_PROXIES_JSON = stringPreferencesKey("paired_proxies_json")
        private val KEY_PAIRED_ROBOT_JSON = stringPreferencesKey("paired_robot_json")
        private val KEY_DUAL_REMINDER = booleanPreferencesKey("dual_reminder_enabled")
    }

    suspend fun getDeviceMode(): DeviceMode =
        dataStore.data.map { DeviceMode.fromStorage(it[KEY_MODE]) }.first()

    suspend fun setDeviceMode(mode: DeviceMode) {
        dataStore.edit { it[KEY_MODE] = mode.name }
    }

    suspend fun isApplyModeRestart(): Boolean =
        dataStore.data.map { it[KEY_APPLY_RESTART] ?: false }.first()

    suspend fun setApplyModeRestart(value: Boolean) {
        dataStore.edit { it[KEY_APPLY_RESTART] = value }
    }

    suspend fun clearApplyModeRestart() {
        setApplyModeRestart(false)
    }

    suspend fun getOrCreateRobotId(): String {
        val existing = dataStore.data.map { it[KEY_ROBOT_ID] }.first()
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        dataStore.edit { it[KEY_ROBOT_ID] = id }
        return id
    }

    suspend fun getOrCreateProxyDeviceId(): String {
        val existing = dataStore.data.map { it[KEY_PROXY_DEVICE_ID] }.first()
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        dataStore.edit { it[KEY_PROXY_DEVICE_ID] = id }
        return id
    }

    suspend fun listPairedProxies(): List<PairedProxyDevice> {
        val raw = dataStore.data.map { it[KEY_PAIRED_PROXIES_JSON] }.first() ?: return emptyList()
        return parseProxies(raw)
    }

    suspend fun hasPairedProxies(): Boolean = listPairedProxies().isNotEmpty()

    suspend fun addPairedProxy(device: PairedProxyDevice) {
        val current = listPairedProxies().toMutableList()
        current.removeAll { it.token == device.token }
        current.add(device)
        saveProxies(current)
    }

    suspend fun updateProxyDeviceId(token: String, proxyDeviceId: String) {
        if (proxyDeviceId.isBlank()) return
        val current = listPairedProxies().map { proxy ->
            if (proxy.token == token && proxy.proxyDeviceId.isBlank()) {
                proxy.copy(
                    proxyDeviceId = proxyDeviceId,
                    label = proxy.label.ifBlank { proxyDeviceId.take(8) },
                )
            } else {
                proxy
            }
        }
        saveProxies(current)
    }

    /**
     * Marks a pending invite as confirmed after proxy handshake.
     */
    suspend fun confirmPairedProxy(
        token: String,
        proxyDeviceId: String,
        label: String,
        lanIp: String = "",
        capabilityPort: Int = PairingQrCodec.DEFAULT_PORT,
    ) {
        val resolvedLabel = label.trim().ifBlank { proxyDeviceId.take(8) }
        val port = capabilityPort.takeIf { it in 1..65535 } ?: PairingQrCodec.DEFAULT_PORT
        val current = listPairedProxies().map { proxy ->
            if (proxy.token == token) {
                proxy.copy(
                    proxyDeviceId = proxyDeviceId.ifBlank { proxy.proxyDeviceId },
                    label = resolvedLabel,
                    lanIp = lanIp.trim().ifBlank { proxy.lanIp },
                    capabilityPort = port,
                )
            } else {
                proxy
            }
        }
        saveProxies(current)
    }

    /** First paired proxy that has a usable LAN address for capability calls. */
    suspend fun primaryCapabilityProxy(): PairedProxyDevice? =
        listPairedProxies().firstOrNull { it.lanIp.isNotBlank() }

    suspend fun isDualReminderEnabled(): Boolean =
        dataStore.data.map { it[KEY_DUAL_REMINDER] ?: false }.first()

    suspend fun setDualReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DUAL_REMINDER] = enabled }
    }

    suspend fun revokePairedProxy(token: String) {
        saveProxies(listPairedProxies().filterNot { it.token == token })
    }

    suspend fun isTokenAuthorized(token: String): Boolean =
        listPairedProxies().any { it.token == token }

    /**
     * Creates a new invite token, stores it as paired, returns QR payload fields (caller adds IP).
     */
    suspend fun createPairingInvite(robotName: String = "MyDeskRobot"): PairingQrPayload {
        val robotId = getOrCreateRobotId()
        val token = UUID.randomUUID().toString()
        addPairedProxy(
            PairedProxyDevice(
                token = token,
                label = "In attesa…",
                createdAtMs = System.currentTimeMillis(),
            ),
        )
        return PairingQrPayload(
            name = robotName,
            ip = "",
            port = PairingQrCodec.DEFAULT_PORT,
            token = token,
            robotId = robotId,
        )
    }

    suspend fun getPairedRobot(): PairedRobotEndpoint? {
        val raw = dataStore.data.map { it[KEY_PAIRED_ROBOT_JSON] }.first() ?: return null
        return parseRobot(raw)
    }

    suspend fun setPairedRobot(endpoint: PairedRobotEndpoint) {
        dataStore.edit {
            it[KEY_PAIRED_ROBOT_JSON] = JSONObject()
                .put("robotId", endpoint.robotId)
                .put("ip", endpoint.ip)
                .put("port", endpoint.port)
                .put("token", endpoint.token)
                .put("name", endpoint.name)
                .toString()
        }
    }

    suspend fun clearPairedRobot() {
        dataStore.edit { it.remove(KEY_PAIRED_ROBOT_JSON) }
    }

    private suspend fun saveProxies(list: List<PairedProxyDevice>) {
        val array = JSONArray()
        list.forEach { proxy ->
            array.put(
                JSONObject()
                    .put("token", proxy.token)
                    .put("proxyDeviceId", proxy.proxyDeviceId)
                    .put("label", proxy.label)
                    .put("createdAtMs", proxy.createdAtMs)
                    .put("lanIp", proxy.lanIp)
                    .put("capabilityPort", proxy.capabilityPort),
            )
        }
        dataStore.edit { it[KEY_PAIRED_PROXIES_JSON] = array.toString() }
    }

    private fun parseProxies(raw: String): List<PairedProxyDevice> {
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        PairedProxyDevice(
                            token = o.getString("token"),
                            proxyDeviceId = o.optString("proxyDeviceId"),
                            label = o.optString("label"),
                            createdAtMs = o.optLong("createdAtMs", 0L),
                            lanIp = o.optString("lanIp"),
                            capabilityPort = o.optInt("capabilityPort", PairingQrCodec.DEFAULT_PORT),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseRobot(raw: String): PairedRobotEndpoint? {
        return try {
            val o = JSONObject(raw)
            PairedRobotEndpoint(
                robotId = o.getString("robotId"),
                ip = o.getString("ip"),
                port = o.getInt("port"),
                token = o.getString("token"),
                name = o.optString("name", "MyDeskRobot"),
            )
        } catch (_: Exception) {
            null
        }
    }
}
