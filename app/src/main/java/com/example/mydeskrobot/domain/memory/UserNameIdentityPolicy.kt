package com.example.mydeskrobot.domain.memory

/**
 * Classifies **stored** user-self name IDENTITY values (document text), not spoken phrases.
 *
 * Used to prevent silent overwrites and to skip notification/system-sourced name claims.
 * Deliberately does **not** gate voice turns by keyword (avoids notification-style shortcuts).
 */
object UserNameIdentityPolicy {

    private val selfNamePatterns = listOf(
        Regex("""(?i)^l['’]utente\s+si\s+chiama\s+(.+)$"""),
        Regex("""(?i)^utente\s+si\s+chiama\s+(.+)$"""),
        Regex("""(?i)^il\s+mio\s+nome\s+[eè]\s+(.+)$"""),
        Regex("""(?i)^mi\s+chiamo\s+(.+)$"""),
    )

    fun isUserSelfNameClaim(value: String): Boolean =
        extractDisplayedName(value) != null

    fun extractDisplayedName(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        for (pattern in selfNamePatterns) {
            val match = pattern.matchEntire(trimmed) ?: continue
            val name = match.groupValues.getOrNull(1)?.trim().orEmpty()
                .trimEnd('.', '!', '?')
                .trim()
            if (name.isNotBlank()) return name
        }
        return null
    }

    fun sameUserName(a: String, b: String): Boolean =
        normalizeName(a) == normalizeName(b)

    fun normalizeName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Extractor: never persist a user-self name IDENTITY when the delta has no user utterance.
     * (Notification / system-only snippets are a common false source.)
     */
    fun shouldSkipExtractorUserSelfName(
        categoryName: String,
        value: String,
        entryRoles: Collection<String>,
    ): Boolean {
        if (!categoryName.equals("IDENTITY", ignoreCase = true)) return false
        if (!isUserSelfNameClaim(value)) return false
        return entryRoles.none { it.equals("user", ignoreCase = true) }
    }
}

/**
 * Turn-scoped gate for tool writes during non-voice system inputs (e.g. notifications).
 * Set by the conversation layer around system-input LLM turns; default allows writes.
 */
object MemoryWriteTurnContext {
    @Volatile
    var blockUserSelfNameIdentity: Boolean = false
}
