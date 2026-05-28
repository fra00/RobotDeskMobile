package com.example.mydeskrobot.reasoning.model

/**
 * Priority level for external inputs.
 * Determines how quickly the input should be processed.
 */
enum class InputPriority {
    /**
     * High priority - process immediately.
     * Used for hardware buttons, emergency signals.
     * Can interrupt or queue ahead of deferred inputs.
     */
    BLOCKING,

    /**
     * Normal priority - process when idle.
     * Used for notifications, sensor readings.
     * Queued if robot is busy (speaking, thinking).
     */
    DEFERRED,
}
