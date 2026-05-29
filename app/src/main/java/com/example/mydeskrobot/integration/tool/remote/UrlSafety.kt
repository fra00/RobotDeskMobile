package com.example.mydeskrobot.integration.tool.remote

import java.net.InetAddress
import java.net.URI

/**
 * Blocks unsafe URLs for server-side fetch (SSRF mitigation).
 */
object UrlSafety {

    fun validateHttpUrl(rawUrl: String): Result<URI> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("URL vuoto"))
        }
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("URL non valido"))
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") {
            return Result.failure(IllegalArgumentException("Solo http/https consentiti"))
        }
        val host = uri.host?.lowercase()
            ?: return Result.failure(IllegalArgumentException("Host mancante"))
        if (host == "localhost" || host.endsWith(".localhost")) {
            return Result.failure(IllegalArgumentException("Host non consentito"))
        }
        if (host == "127.0.0.1" || host == "::1" || host == "0.0.0.0") {
            return Result.failure(IllegalArgumentException("Host non consentito"))
        }
        if (isPrivateOrLinkLocalHost(host)) {
            return Result.failure(IllegalArgumentException("Rete privata non consentita"))
        }
        return Result.success(uri)
    }

    private fun isPrivateOrLinkLocalHost(host: String): Boolean {
        return try {
            val addresses = InetAddress.getAllByName(host)
            addresses.any { address ->
                address.isAnyLocalAddress ||
                    address.isLoopbackAddress ||
                    address.isLinkLocalAddress ||
                    address.isSiteLocalAddress
            }
        } catch (_: Exception) {
            // If DNS fails, allow OkHttp to fail later (public hosts only in practice)
            false
        }
    }
}
