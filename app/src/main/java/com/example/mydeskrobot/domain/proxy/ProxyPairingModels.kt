package com.example.mydeskrobot.domain.proxy

/**
 * A proxy phone authorized to POST notifications to this robot.
 */
data class PairedProxyDevice(
    val token: String,
    val proxyDeviceId: String = "",
    val label: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
    /** Proxy LAN IPv4 for Robot→Proxy capability calls; empty until pair handshake. */
    val lanIp: String = "",
    /** HTTP port of the capability server on the proxy (default 8765). */
    val capabilityPort: Int = 8765,
)

/**
 * Robot endpoint stored on a proxy device after QR scan.
 */
data class PairedRobotEndpoint(
    val robotId: String,
    val ip: String,
    val port: Int,
    val token: String,
    val name: String = "MyDeskRobot",
)

/**
 * JSON fields encoded in the pairing QR (spec §6.2).
 */
data class PairingQrPayload(
    val v: Int = 1,
    val name: String,
    val ip: String,
    val port: Int,
    val token: String,
    val robotId: String,
)
