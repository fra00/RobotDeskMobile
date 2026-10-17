package com.example.mydeskrobot.domain.messaging

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Builds WhatsApp deep-link URL strings that open a chat with a pre-filled message.
 * The user must tap Send in WhatsApp — same limitation as ACTION_DIAL for phone calls.
 *
 * Returns plain [String] URLs (JVM-safe); callers parse [android.net.Uri] at the Intent boundary.
 */
object WhatsAppUriBuilder {

    fun buildSendUri(sendId: String, message: String): String {
        val id = normalizeSendId(sendId)
        val text = encodeQuery(message.trim())
        return "https://api.whatsapp.com/send?phone=$id&text=$text"
    }

    fun buildFallbackSchemeUri(sendId: String, message: String): String {
        val id = normalizeSendId(sendId)
        val text = encodeQuery(message.trim())
        return "whatsapp://send?phone=$id&text=$text"
    }

    fun normalizeSendId(raw: String): String =
        raw.trim()
            .removePrefix("+")
            .removeSuffix("@g.us")
            .removeSuffix("@s.whatsapp.net")
            .filter { it.isDigit() }

    /** Same space-as-'+' behaviour as Android [android.net.Uri.encode] for query values. */
    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}
