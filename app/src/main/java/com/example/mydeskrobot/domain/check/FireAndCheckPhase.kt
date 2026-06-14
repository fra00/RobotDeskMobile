package com.example.mydeskrobot.domain.check

enum class FireAndCheckPhase {
    /** Primary action not fired yet. */
    SCHEDULED,
    /** Primary fired; waiting for verification step. */
    AWAITING_VERIFICATION,
    /** Verification window active — check should run. */
    CHECK_PENDING,
}
