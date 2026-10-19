package com.example.mydeskrobot.domain.activitylog

enum class EpisodeKind {
    PHYSICAL_NOW,
    PLAN,
    SOCIAL_THREAD,
    /** Work chat / collaboration (Teams, Slack) — not personal social life. */
    WORK_THREAD,
    COMMITMENT,
}
