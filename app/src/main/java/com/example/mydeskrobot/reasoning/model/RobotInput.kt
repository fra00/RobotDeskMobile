package com.example.mydeskrobot.reasoning.model

/**
 * Sealed hierarchy for all external inputs to the robot.
 * These are system events (not voice) that can trigger LLM processing.
 */
sealed class RobotInput {
    abstract val sourceId: String
    abstract val timestamp: Long
    abstract val priority: InputPriority

    /**
     * Notification from another app (WhatsApp, SMS, etc.).
     */
    data class Notification(
        val packageName: String,
        val appLabel: String,
        val title: String?,
        val text: String?,
        val notificationKey: String,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "notification"
        override val priority: InputPriority = InputPriority.DEFERRED
    }

    /**
     * Physical button press on the robot hardware (ESP32).
     * Stub for future implementation.
     */
    data class HardwareButton(
        val buttonId: String,
        val action: String,
        val payload: Map<String, Any?> = emptyMap(),
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "hardware_button"
        override val priority: InputPriority = InputPriority.BLOCKING
    }

    /**
     * Ambient sensor reading (temperature, light, etc.).
     * Stub for future implementation.
     */
    data class SensorReading(
        val sensorType: String,
        val value: Double,
        val unit: String,
        val thresholdCrossed: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "sensor_$sensorType"
        override val priority: InputPriority = InputPriority.DEFERRED
    }

    /**
     * A user-scheduled task reached its trigger time (e.g. voice reminder).
     */
    data class ScheduledTaskFired(
        val taskId: Long,
        val message: String,
        val triggerAtMillis: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "scheduled_task"
        override val priority: InputPriority = InputPriority.DEFERRED
    }

    /**
     * Weekly self-reflection trigger.
     * The robot analyzes its behavior and learns what works.
     */
    data class WeeklyReflection(
        /** Total user interactions this week. */
        val totalInteractions: Int,
        /** Total proactive speaks this week. */
        val totalProactiveSpeaks: Int,
        /** Proactive speaks with positive user engagement. */
        val positiveResponses: Int,
        /** Proactive speaks that were ignored. */
        val ignoredSuggestions: Int,
        /** Topics that led to positive engagement. */
        val usefulTopics: List<String>,
        /** Topics that were ignored or disliked. */
        val ignoredTopics: List<String>,
        /** Success rate as percentage (0-100). */
        val successRatePercent: Int,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "weekly_reflection"
        override val priority: InputPriority = InputPriority.DEFERRED
    }

    /**
     * Predictivity deviation: habitual activity missing today at the expected time window.
     */
    data class PredictivityDeviation(
        val slotKey: String,
        val displayLabel: String,
        val typicalTimeMinutes: Int,
        val hitCount: Int,
        val confidence: Float,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "predictivity_deviation"
        override val priority: InputPriority = InputPriority.DEFERRED
    }

    /**
     * Daily wellness check: score enabled domains (built-in + custom) and optionally speak once.
     */
    data class WellnessCheck(
        val phase: com.example.mydeskrobot.domain.wellness.WellnessPhase,
        val enabledDomainIds: Set<String> = emptySet(),
        val customDomains: List<com.example.mydeskrobot.domain.wellness.WellnessCustomDomain> = emptyList(),
        val habitProfileSummary: String? = null,
        val recentDailyActivities: List<String> = emptyList(),
        val activePatterns: List<String> = emptyList(),
        val recentObservations: List<String> = emptyList(),
        val orderObservationFresh: String? = null,
        val bodyConfigured: Boolean = false,
        val bodyReachable: Boolean = false,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "wellness_check"
        override val priority: InputPriority = InputPriority.DEFERRED
    }
}
