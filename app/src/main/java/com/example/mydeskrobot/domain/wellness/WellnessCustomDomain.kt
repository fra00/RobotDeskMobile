package com.example.mydeskrobot.domain.wellness

/**
 * User-authored domain evaluated in the same Wellness tick as built-in care domains.
 */
data class WellnessCustomDomain(
    val id: String,
    val displayName: String,
    val prompt: String,
)
