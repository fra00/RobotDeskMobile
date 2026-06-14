package com.example.mydeskrobot.domain.telephony

/**
 * Normalizes phone numbers for [android.content.Intent.ACTION_DIAL] tel: URIs.
 */
object PhoneNumberNormalizer {

    private val DISALLOWED_CHARS = Regex("""[^\d+*#]""")

    /**
     * Returns a dial-ready number (digits with optional leading +) or null if invalid.
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val compact = trimmed.replace(DISALLOWED_CHARS, "")
        if (compact.isEmpty()) return null

        val normalized = when {
            compact.startsWith("+") -> "+" + compact.drop(1).filter { it.isDigit() }
            compact.startsWith("00") -> "+" + compact.drop(2).filter { it.isDigit() }
            else -> compact.filter { it.isDigit() || it == '#' || it == '*' }
        }

        val digitCount = normalized.count { it.isDigit() }
        if (digitCount < 3 || digitCount > 15) return null
        return normalized
    }
}
