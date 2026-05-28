package com.example.mydeskrobot.integration.input

import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

/**
 * Contract for external input sources (notifications, hardware, sensors).
 * Each source knows how to filter, deduplicate, and format its inputs.
 */
interface InputSource {
    /** Unique identifier for this source (e.g., "notification", "hardware_button") */
    val id: String

    /** Default priority for inputs from this source */
    val priority: InputPriority

    /** User-visible name for Settings UI */
    val displayName: String

    /**
     * Whether this source is currently enabled.
     * Returns false if:
     * - Required OS permission is not granted
     * - User disabled this source in Settings
     * - Hardware is not connected
     */
    fun isEnabled(): Boolean

    /**
     * Convert raw input data to a [RobotInput].
     * Returns null if the raw data is invalid.
     */
    fun normalize(raw: Any): RobotInput?

    /**
     * Local filter before queueing.
     * Returns false to drop the input (spam, duplicate, blacklisted, etc.).
     */
    fun shouldAccept(input: RobotInput): Boolean

    /**
     * Create an envelope with formatted LLM content and dedup key.
     */
    fun toEnvelope(input: RobotInput): SystemInputEnvelope

    /**
     * Generate a deduplication key for the input.
     * Inputs with the same key within the TTL window are considered duplicates.
     */
    fun toDedupKey(input: RobotInput): String
}
