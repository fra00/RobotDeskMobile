package com.example.mydeskrobot.domain.check

/**
 * PHYSICAL = sensor/presence verification (classic fire-and-check).
 * COMMITMENT = dialogic “did you do X?” after an urgent user promise.
 */
enum class FireAndCheckKind {
    PHYSICAL,
    COMMITMENT,
}
