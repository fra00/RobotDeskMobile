package com.example.mydeskrobot.domain.activitylog

/**
 * Deterministic work vs personal communication channels (v1: Teams + Slack).
 * Used at notification write and to coerce episodic extractor mistakes.
 */
object WorkCommunicationChannels {

    val WORK_PACKAGES: Set<String> = setOf(
        "com.microsoft.teams",
        "com.microsoft.skype.teams",
        "com.Slack",
        "com.slack",
    )

    fun isWorkPackage(packageName: String?): Boolean {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty()) return false
        return pkg in WORK_PACKAGES
    }

    fun isWorkChannelLabel(sourceChannelOrAppLabel: String?): Boolean {
        val raw = sourceChannelOrAppLabel?.trim()?.lowercase().orEmpty()
        if (raw.isEmpty()) return false
        if (raw == "teams" || raw == "slack") return true
        if (raw.contains("microsoft teams")) return true
        // Whole-token Slack / Teams (avoid matching unrelated substrings).
        return raw.split(Regex("[^a-z0-9]+")).any { it == "slack" || it == "teams" }
    }

    fun episodeKindForNotification(
        packageName: String?,
        appLabel: String? = null,
    ): EpisodeKind {
        if (isWorkPackage(packageName) || isWorkChannelLabel(appLabel)) {
            return EpisodeKind.WORK_THREAD
        }
        return EpisodeKind.SOCIAL_THREAD
    }

    /**
     * If LLM/extractor classified a work channel as personal social, coerce to [EpisodeKind.WORK_THREAD].
     */
    fun coerceExtractedKind(
        kind: EpisodeKind,
        sourceChannel: String?,
    ): EpisodeKind {
        if (kind == EpisodeKind.SOCIAL_THREAD && isWorkChannelLabel(sourceChannel)) {
            return EpisodeKind.WORK_THREAD
        }
        return kind
    }
}
