package com.example.mydeskrobot.domain.messaging

import android.net.Uri

/**
 * Builds WhatsApp deep links that open a chat with a pre-filled message.
 * The user must tap Send in WhatsApp — same limitation as ACTION_DIAL for phone calls.
 */
object WhatsAppUriBuilder {

    fun buildSendUri(sendId: String, message: String): Uri {
        val id = normalizeSendId(sendId)
        val text = Uri.encode(message.trim())
        return Uri.parse("https://api.whatsapp.com/send?phone=$id&text=$text")
    }

    fun buildFallbackSchemeUri(sendId: String, message: String): Uri {
        val id = normalizeSendId(sendId)
        val text = Uri.encode(message.trim())
        return Uri.parse("whatsapp://send?phone=$id&text=$text")
    }

    fun normalizeSendId(raw: String): String =
        raw.trim()
            .removePrefix("+")
            .removeSuffix("@g.us")
            .removeSuffix("@s.whatsapp.net")
            .filter { it.isDigit() }
}
