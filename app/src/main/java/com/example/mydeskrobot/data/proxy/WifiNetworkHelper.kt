package com.example.mydeskrobot.data.proxy

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

object WifiNetworkHelper {

    fun isOnWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Best-effort IPv4 on Wi‑Fi for QR pairing.
     */
    fun wifiIpv4Address(context: Context): String? {
        val fromWifiManager = wifiManagerIp(context)
        if (!fromWifiManager.isNullOrBlank() && fromWifiManager != "0.0.0.0") {
            return fromWifiManager
        }
        return firstNonLoopbackIpv4()
    }

    @Suppress("DEPRECATION")
    private fun wifiManagerIp(context: Context): String? {
        return try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifi.connectionInfo?.ipAddress ?: return null
            if (ip == 0) return null
            String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun firstNonLoopbackIpv4(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { addr ->
                    !addr.isLoopbackAddress && addr is Inet4Address
                }
                ?.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
