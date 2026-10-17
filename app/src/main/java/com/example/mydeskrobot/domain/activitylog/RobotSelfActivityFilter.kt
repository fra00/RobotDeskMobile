package com.example.mydeskrobot.domain.activitylog

/**
 * Marks and detects **robot-only** activities so they never mix with the user's Log Day.
 *
 * New robot pastimes MUST be written with [LABEL_PREFIX] (see [tagLabel]).
 * Legacy untagged lines are still detected via channel / phrase heuristics.
 */
object RobotSelfActivityFilter {

    /** Stable prefix on labels / log text: "[robot] Ha iniziato a giocare a Pong". */
    const val LABEL_PREFIX = "[robot]"

    /** Conversation-log system channel used by idle distraction overlays. */
    const val CHANNEL_SVAGO = "svago"

    /** Optional sourceChannel / actor marker when persisted as an episode. */
    const val SOURCE_ROBOT = "robot"

    /** Exact tokens from [com.example.mydeskrobot.domain.mood.IdleDistractionKind.promptToken] (legacy). */
    private val exactOverlayLabels = setOf(
        "musica",
        "lettura",
        "assente",
        "pong",
    )

    private val phraseMarkers = listOf(
        "pong",
        "cuffie",
        "sessione pong",
        "gioco pong",
        "giocare a pong",
        "torno subito",
        "ha iniziato a leggere",
        "ha smesso di leggere",
        "ha iniziato ad ascoltare",
        "ha smesso di ascoltare",
        "ha iniziato a giocare",
        "ha smesso di giocare",
        "si è allontanato",
        "è tornato dallo",
        "fine sessione pong",
    )

    fun tagLabel(plainDescription: String): String {
        val cleaned = plainDescription.trim()
            .removePrefix(LABEL_PREFIX)
            .trimStart(':', ' ', '-')
            .trim()
        if (cleaned.isBlank()) return LABEL_PREFIX
        return "$LABEL_PREFIX $cleaned"
    }

    fun isRobotTagged(text: String): Boolean {
        val normalized = text.trimStart().lowercase()
        return normalized.startsWith(LABEL_PREFIX) ||
            normalized.startsWith("robot:") ||
            normalized.startsWith("robot ")
    }

    fun isRobotSelfChannel(channel: String?): Boolean {
        val c = channel?.trim().orEmpty()
        if (c.isEmpty()) return false
        return c.equals(CHANNEL_SVAGO, ignoreCase = true) ||
            c.equals(SOURCE_ROBOT, ignoreCase = true)
    }

    fun isRobotActor(actor: String?): Boolean {
        val a = actor?.trim().orEmpty()
        if (a.isEmpty()) return false
        return a.equals(SOURCE_ROBOT, ignoreCase = true) ||
            a.equals("robot", ignoreCase = true) ||
            isRobotTagged(a)
    }

    /**
     * Episodic parser stores system lines as `"channel: body"`.
     */
    fun isExcludedSystemEntryText(systemEntryText: String): Boolean {
        val trimmed = systemEntryText.trim()
        val colon = trimmed.indexOf(':')
        val channel = if (colon >= 0) trimmed.substring(0, colon).trim() else trimmed
        if (isRobotSelfChannel(channel)) return true
        val body = if (colon >= 0) trimmed.substring(colon + 1) else trimmed
        return isRobotSelfActivity(body)
    }

    /** True if this text/label must not enter user Log Day / habits / "cosa ho fatto". */
    fun isRobotSelfActivity(
        label: String,
        sourceChannel: String? = null,
        actor: String? = null,
    ): Boolean {
        if (isRobotSelfChannel(sourceChannel)) return true
        if (isRobotActor(actor)) return true
        if (isRobotTagged(label)) return true
        return matchesLegacyRobotPastime(label)
    }

    fun isRobotSelfLabel(label: String): Boolean = isRobotSelfActivity(label)

    fun isRobotSelfHabit(
        displayLabel: String,
        canonicalLabel: String = "",
        rawLabels: Set<String> = emptySet(),
    ): Boolean {
        if (isRobotSelfActivity(displayLabel)) return true
        if (canonicalLabel.isNotBlank() && isRobotSelfActivity(canonicalLabel)) return true
        return rawLabels.any { isRobotSelfActivity(it) }
    }

    fun isUserFacingEpisode(
        label: String,
        sourceChannel: String? = null,
        actor: String? = null,
    ): Boolean = !isRobotSelfActivity(label, sourceChannel, actor)

    private fun matchesLegacyRobotPastime(label: String): Boolean {
        val normalized = label.trim().lowercase().replace('_', ' ')
        if (normalized.isBlank()) return false
        // Strip prefix if present then re-check body only for markers.
        val body = normalized
            .removePrefix(LABEL_PREFIX)
            .trimStart(':', ' ')
            .trim()
        if (body in exactOverlayLabels) return true
        return phraseMarkers.any { marker -> body.contains(marker) }
    }
}
