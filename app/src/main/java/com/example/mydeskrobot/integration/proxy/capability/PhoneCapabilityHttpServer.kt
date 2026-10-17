package com.example.mydeskrobot.integration.proxy.capability

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.health.HealthConnectExerciseReaderImpl
import com.example.mydeskrobot.data.proxy.DeviceModeRepository
import com.example.mydeskrobot.data.proxy.PairingQrCodec
import com.example.mydeskrobot.data.proxy.WifiNetworkHelper
import com.example.mydeskrobot.integration.proxy.capability.handlers.ContactsCapabilityHandler
import com.example.mydeskrobot.integration.proxy.capability.handlers.ExercisesCapabilityHandler
import com.example.mydeskrobot.integration.proxy.capability.handlers.LocalNotificationCapabilityHandler
import com.example.mydeskrobot.integration.proxy.capability.handlers.PingCapabilityHandler
import com.example.mydeskrobot.integration.telephony.AndroidContactsPhoneResolver
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.IOException

/**
 * LAN HTTP server on the **PROXY** device: GET/POST `/v1/capabilities/{id}`.
 */
class PhoneCapabilityHttpServer(
    private val appContext: Context,
    private val modeRepository: DeviceModeRepository,
    private val registry: PhoneCapabilityRegistry,
) : NanoHTTPD(PairingQrCodec.DEFAULT_PORT) {

    companion object {
        private const val TAG = "PhoneCapHttp"

        @Volatile
        private var instance: PhoneCapabilityHttpServer? = null

        fun defaultRegistry(context: Context): PhoneCapabilityRegistry {
            val app = context.applicationContext
            return PhoneCapabilityRegistry(
                mapOf(
                    PhoneCapabilityId.PING to PingCapabilityHandler(),
                    PhoneCapabilityId.EXERCISES to ExercisesCapabilityHandler(
                        HealthConnectExerciseReaderImpl(app),
                    ),
                    PhoneCapabilityId.CONTACTS to ContactsCapabilityHandler(
                        AndroidContactsPhoneResolver(app),
                    ),
                    PhoneCapabilityId.LOCAL_NOTIFICATION to LocalNotificationCapabilityHandler(app),
                ),
            )
        }

        fun startIfNeeded(context: Context) {
            val app = context.applicationContext
            synchronized(this) {
                if (instance?.wasStarted() == true) return
                if (!WifiNetworkHelper.isOnWifi(app)) {
                    Log.i(TAG, "Skip capability server: not on Wi-Fi")
                    return
                }
                val repo = DeviceModeRepository(app)
                val server = PhoneCapabilityHttpServer(app, repo, defaultRegistry(app))
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                    instance = server
                    Log.i(TAG, "Capability listening on :${PairingQrCodec.DEFAULT_PORT}")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to start capability server", e)
                }
            }
        }

        fun stop() {
            synchronized(this) {
                instance?.stop()
                instance = null
                Log.i(TAG, "Capability HTTP server stopped")
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        if (!WifiNetworkHelper.isOnWifi(appContext)) {
            return jsonError(Response.Status.FORBIDDEN, "wifi_required")
        }
        val path = session.uri.trimEnd('/')
        if (!path.startsWith("/v1/capabilities/")) {
            return jsonError(Response.Status.NOT_FOUND, "not_found")
        }
        val wireId = path.removePrefix("/v1/capabilities/").substringBefore('/').trim()
        if (wireId.isEmpty()) {
            return jsonError(Response.Status.NOT_FOUND, "not_found")
        }

        val token = extractToken(session)
            ?: return jsonError(Response.Status.UNAUTHORIZED, "missing_token")
        val authorized = runBlocking {
            val robot = modeRepository.getPairedRobot()
            robot != null && robot.token == token
        }
        if (!authorized) {
            return jsonError(Response.Status.UNAUTHORIZED, "invalid_token")
        }

        val handler = registry.getByWireId(wireId)
            ?: return jsonError(Response.Status.NOT_FOUND, "unknown_capability")

        val params = mutableMapOf<String, String>()
        session.parameters.forEach { (k, values) ->
            values.firstOrNull()?.let { params[k] = it }
        }
        if (session.method == Method.POST) {
            val body = readBody(session)
            if (!body.isNullOrBlank()) {
                try {
                    val o = JSONObject(body)
                    o.keys().forEach { key ->
                        params[key] = o.opt(key)?.toString().orEmpty()
                    }
                } catch (_: Exception) {
                    return jsonError(Response.Status.BAD_REQUEST, "malformed")
                }
            }
        }

        return try {
            val payload = runBlocking { handler.handle(params) }
            val wrapped = JSONObject().put("ok", true)
            payload.keys().forEach { key -> wrapped.put(key, payload.get(key)) }
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                wrapped.toString(),
            )
        } catch (e: PhoneCapabilityException) {
            val status = when (e.httpStatus) {
                400 -> Response.Status.BAD_REQUEST
                401 -> Response.Status.UNAUTHORIZED
                403 -> Response.Status.FORBIDDEN
                404 -> Response.Status.NOT_FOUND
                else -> Response.Status.INTERNAL_ERROR
            }
            jsonError(status, e.errorCode)
        } catch (e: Exception) {
            Log.w(TAG, "Capability $wireId failed", e)
            jsonError(Response.Status.INTERNAL_ERROR, "handler_error")
        }
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

    private fun jsonError(status: Response.Status, code: String): Response =
        newFixedLengthResponse(status, "application/json", """{"ok":false,"error":"$code"}""")
}
