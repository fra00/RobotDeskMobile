package com.example.mydeskrobot.domain.presence

/**
 * Decides when the robot should briefly center on the user (not continuous tracking).
 */
object AttentionTriggerMatcher {

    /** Every non-empty user utterance in an active voice session. */
    fun shouldCenterOnUser(phrase: String): Boolean = phrase.isNotBlank()
}
