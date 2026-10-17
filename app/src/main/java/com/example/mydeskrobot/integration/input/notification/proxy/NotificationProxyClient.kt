package com.example.mydeskrobot.integration.input.notification.proxy

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.data.proxy.PairingQrCodec
import com.example.mydeskrobot.data.proxy.WifiNetworkHelper
import com.example.mydeskrobot.integration.input.notification.NotificationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result of a proxy→robot HTTP call.
 */
sealed class ProxyHttpResult {
    data object Ok : ProxyHttpResult()
    data class Failed(val message: String) : ProxyHttpResult()
}

/**
 * HTTP client on the PROXY device (pair handshake + notify forward).
 */
class NotificationProxyClient(
    private val appContext: Context,
    private val modeRepository: DeviceModeRepository,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        private const val TAG = "NotifProxyClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Confirms pairing with the robot after QR scan.
     * Returns Ok only on HTTP 2xx; otherwise Failed with a user-visible reason.
     */
    suspend fun confirmPairing(
        ip: String,
        port: Int,
        token: String,
    ): ProxyHttpResult = withContext(Dispatchers.IO) {
        if (!WifiNetworkHelper.isOnWifi(appContext)) {
            return@withContext ProxyHttpResult.Failed("Serve la stessa Wi‑Fi del robot")
        }
        val proxyDeviceId = modeRepository.getOrCreateProxyDeviceId()
        val proxyLabel = Build.MODEL?.takeIf { it.isNotBlank() } ?: proxyDeviceId.take(8)
        val lanIp = WifiNetworkHelper.wifiIpv4Address(appContext).orEmpty()
        val body = JSONObject()
            .put("protocolVersion", 1)
            .put("proxyDeviceId", proxyDeviceId)
            .put("proxyLabel", proxyLabel)
            .put("lanIp", lanIp)
            .put("capabilityPort", PairingQrCodec.DEFAULT_PORT)
            .toString()
        val url = "http://$ip:$port/v1/pair"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(body.toRequestBody(JSON))
            .build()
        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Pairing confirmed with $url")
                    ProxyHttpResult.Ok
                } else {
                    val code = response.code
                    Log.w(TAG, "Pairing failed HTTP $code for $url")
                    ProxyHttpResult.Failed(
                        when (code) {
                            401 -> "Token non valido (rigenera il QR sul robot)"
                            403 -> "Robot non in Wi‑Fi o rifiuta il collegamento"
                            else -> "Robot ha risposto $code (atteso 200)"
                        },
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pairing call failed: ${e.message}")
            ProxyHttpResult.Failed(
                "Robot non risponde ($ip:$port). Stessa Wi‑Fi? App robot aperta?",
            )
        }
    }

    /**
     * @return true if HTTP 2xx, false if dropped
     */
    suspend fun forward(data: NotificationData): Boolean = withContext(Dispatchers.IO) {
        if (!WifiNetworkHelper.isOnWifi(appContext)) {
            Log.d(TAG, "Drop: not on Wi-Fi")
            return@withContext false
        }
        val endpoint = modeRepository.getPairedRobot()
        if (endpoint == null) {
            Log.d(TAG, "Drop: no paired robot")
            return@withContext false
        }
        val proxyDeviceId = modeRepository.getOrCreateProxyDeviceId()
        val body = JSONObject()
            .put("protocolVersion", 1)
            .put("packageName", data.packageName)
            .put("notificationKey", data.key)
            .put("title", data.title)
            .put("text", data.text)
            .put("postTime", data.postTime)
            .put("proxyDeviceId", proxyDeviceId)
            .toString()
        val url = "http://${endpoint.ip}:${endpoint.port}/v1/notify"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${endpoint.token}")
            .post(body.toRequestBody(JSON))
            .build()
        return@withContext try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Forwarded ${data.packageName} → $url")
                    true
                } else {
                    Log.w(TAG, "Drop: HTTP ${response.code} for $url")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Drop: ${e.message}")
            false
        }
    }
}
