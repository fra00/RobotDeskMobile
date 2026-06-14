package com.example.mydeskrobot.domain.telephony

/**
 * Extracts dialable numbers from free-form text (e.g. memory values).
 */
object PhoneNumberExtractor {

    private val CANDIDATE = Regex("""(?:\+|00)?[\d][\d\s.\-/]{5,}[\d]""")

    fun extractFirst(text: String): String? {
        for (match in CANDIDATE.findAll(text)) {
            PhoneNumberNormalizer.normalize(match.value)?.let { return it }
        }
        return null
    }
}
