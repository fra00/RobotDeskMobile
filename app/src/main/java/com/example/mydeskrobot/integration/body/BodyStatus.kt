package com.example.mydeskrobot.integration.body

import com.squareup.moshi.Json

data class BodyJointState(
    val position: Int = 0,
    val target: Int = 0,
    val min: Int = -BodyJoint.LIMIT_DEG,
    val max: Int = BodyJoint.LIMIT_DEG,
)

data class BodyStatus(
    val joints: Map<String, BodyJointState> = emptyMap(),
    val moving: Boolean = false,
    val ip: String? = null,
    val hostname: String? = null,
    val urlIp: String? = null,
    val urlMdns: String? = null,
    val uptimeMs: Long? = null,
    val rssi: Int? = null,
)

/** Raw JSON shape from GET /status */
internal data class BodyStatusJson(
    val joints: Map<String, BodyJointState>? = null,
    val moving: Boolean? = null,
    val ip: String? = null,
    val hostname: String? = null,
    @Json(name = "url_ip") val urlIp: String? = null,
    @Json(name = "url_mdns") val urlMdns: String? = null,
    @Json(name = "uptime_ms") val uptimeMs: Long? = null,
    val rssi: Int? = null,
) {
    fun toDomain(): BodyStatus = BodyStatus(
        joints = joints.orEmpty(),
        moving = moving == true,
        ip = ip,
        hostname = hostname,
        urlIp = urlIp,
        urlMdns = urlMdns,
        uptimeMs = uptimeMs,
        rssi = rssi,
    )
}

data class BodyOkResponse(
    val ok: Boolean? = null,
    val error: String? = null,
    val message: String? = null,
    val joint: String? = null,
    val position: Int? = null,
    val target: Int? = null,
)
