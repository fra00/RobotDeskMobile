package com.example.mydeskrobot.integration.input.notification.proxy

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.input.InputSettingsRepository
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.data.proxy.PairingQrCodec
import com.example.mydeskrobot.data.proxy.WifiNetworkHelper
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.integration.input.notification.NotificationData
import com.example.mydeskrobot.integration.input.notification.NotificationInputSource
import com.example.mydeskrobot.reasoning.model.RobotInput
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.IOException

/**
 * LAN HTTP server on the ROBOT device:
 * - POST /v1/pair → confirm proxy handshake
 * - POST /v1/notify → SystemInputDispatcher
 */
class NotificationProxyHttpServer(
    private val appContext: Context,
    private val modeRepository: DeviceModeRepository,
    private val inputSource: NotificationInputSource,
) : NanoHTTPD(PairingQrCodec.DEFAULT_PORT) {

    companion object {
        private const val TAG = "NotifProxyHttp"

        @Volatile
        private var instance: NotificationProxyHttpServer? = null

        fun startIfNeeded(context: Context) {
            val app = context.applicationContext
            synchronized(this) {
                if (instance?.wasStarted() == true) return
                if (!WifiNetworkHelper.isOnWifi(app)) {
                    Log.i(TAG, "Skip HTTP server: not on Wi-Fi")
                    return
                }
                val repo = DeviceModeRepository(app)
                val source = NotificationInputSource(
                    settingsRepository = InputSettingsRepository(app),
                    packageManager = app.packageManager,
                )
                val server = NotificationProxyHttpServer(app, repo, source)
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    instance = server
                    Log.i(TAG, "Listening on :${PairingQrCodec.DEFAULT_PORT}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to start HTTP server", e)
                }
            }
        }

        fun stop() {
            synchronized(this) {
                instance?.stop()
                instance = null
                Log.i(TAG, "HTTP server stopped")
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (!WifiNetworkHelper.isOnWifi(appContext)) {
            return jsonError(Response.Status.FORBIDDEN, "wifi_required")
        }
        if (session.method != Method.POST) {
            return jsonError(Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed")
        }
        return when (session.uri.trimEnd('/')) {
            "/v1/pair" -> handlePair(session)
            "/v1/notify" -> handleNotify(session)
            else -> jsonError(Response.Status.NOT_FOUND, "not_found")
        }
    }

    private fun handlePair(session: IHTTPSession): Response {
        val token = extractToken(session)
            ?: return jsonError(Response.Status.UNAUTHORIZED, "missing_token")
        val authorized = runBlocking { modeRepository.isTokenAuthorized(token) }
        if (!authorized) {
            return jsonError(Response.Status.UNAUTHORIZED, "invalid_token")
        }

        val body = readBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "empty_body")
        val pair = parsePairBody(body)
            ?: return jsonError(Response.Status.BAD_REQUEST, "malformed")

        runBlocking {
            modeRepository.confirmPairedProxy(
                token = token,
                proxyDeviceId = pair.proxyDeviceId,
                label = pair.proxyLabel,
                lanIp = pair.lanIp,
                capabilityPort = pair.capabilityPort,
            )
        }
        Log.i(
            TAG,
            "Pairing confirmed proxy=${pair.proxyDeviceId} label=${pair.proxyLabel} " +
                "lanIp=${pair.lanIp} port=${pair.capabilityPort}",
        )
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"ok":true}""",
        )
    }

    private fun handleNotify(session: IHTTPSession): Response {
        val token = extractToken(session)
            ?: return jsonError(Response.Status.UNAUTHORIZED, "missing_token")
        val authorized = runBlocking { modeRepository.isTokenAuthorized(token) }
        if (!authorized) {
            return jsonError(Response.Status.UNAUTHORIZED, "invalid_token")
        }

        val body = readBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "empty_body")
        val data = parseNotifyBody(body)
            ?: return jsonError(Response.Status.BAD_REQUEST, "malformed")

        runBlocking {
            modeRepository.updateProxyDeviceId(token, data.proxyDeviceId)
        }

        val robotInput = inputSource.normalize(
            NotificationData(
                packageName = data.packageName,
                key = data.notificationKey,
                title = data.title,
                text = data.text,
                postTime = data.postTime,
                isGroupSummary = false,
            ),
        ) as? RobotInput.Notification
            ?: return jsonError(Response.Status.BAD_REQUEST, "normalize_failed")

        if (robotInput.title.isNullOrBlank() && robotInput.text.isNullOrBlank()) {
            return jsonError(Response.Status.BAD_REQUEST, "empty_notification")
        }

        val envelope = inputSource.toEnvelope(robotInput)
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
        Log.i(TAG, "Accepted remote notification pkg=${data.packageName}")
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            """{"ok":true}""",
        )
    }

    private fun extractToken(session: IHTTPSession): String? {
        val header = session.headers["authorization"] ?: session.headers["Authorization"]
        if (!header.isNullOrBlank() && header.startsWith("Bearer ", ignoreCase = true)) {
            return header.substring(7).trim().ifBlank { null }
        }
        return null
    }

    private fun readBody(session: IHTTPSession): String? {
        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            map["postData"]
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read body", e)
            null
        }
    }

    private fun parsePairBody(raw: String): RemotePairBody? {
        return try {
            val o = JSONObject(raw)
            val proxyDeviceId = o.optString("proxyDeviceId").trim()
            if (proxyDeviceId.isEmpty()) return null
            RemotePairBody(
                proxyDeviceId = proxyDeviceId,
                proxyLabel = o.optString("proxyLabel").trim().ifBlank { proxyDeviceId.take(8) },
                lanIp = o.optString("lanIp").trim(),
                capabilityPort = o.optInt("capabilityPort", PairingQrCodec.DEFAULT_PORT)
                    .takeIf { it in 1..65535 } ?: PairingQrCodec.DEFAULT_PORT,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNotifyBody(raw: String): RemoteNotifyBody? {
        return try {
            val o = JSONObject(raw)
            RemoteNotifyBody(
                packageName = o.getString("packageName"),
                notificationKey = o.getString("notificationKey"),
                title = o.optString("title").takeIf { it.isNotBlank() },
                text = o.optString("text").takeIf { it.isNotBlank() },
                postTime = o.optLong("postTime", System.currentTimeMillis()),
                proxyDeviceId = o.optString("proxyDeviceId"),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonError(status: Response.Status, code: String): Response =
        newFixedLengthResponse(status, "application/json", """{"ok":false,"error":"$code"}""")

    private data class RemotePairBody(
        val proxyDeviceId: String,
        val proxyLabel: String,
        val lanIp: String,
        val capabilityPort: Int,
    )

    private data class RemoteNotifyBody(
        val packageName: String,
        val notificationKey: String,
        val title: String?,
        val text: String?,
        val postTime: Long,
        val proxyDeviceId: String,
    )
}
