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
     * Periodic tick for autonomous agent proactivity.
     * The robot evaluates context and decides whether to speak or stay silent.
     */
    data class Heartbeat(
        val minutesSinceLastInteraction: Long,
        val currentHour: Int,
        val currentMinute: Int,
        val dayOfWeek: String,
        val pendingRemindersCount: Int,
        val relevantRoutines: List<String>,
        /** Current autonomous mood valence (-1…+1 wellbeing). */
        val moodValence: Float? = null,
        /** Current autonomous mood of the robot (e.g. "bored", "happy"). */
        val moodLabel: String? = null,
        /** Current mood intensity (0.0–1.0). */
        val moodIntensity: Float? = null,
        /** Number of user interactions today. */
        val todayInteractions: Int = 0,
        /** Number of proactive speaks today (to avoid being too chatty). */
        val proactiveSpeaksToday: Int = 0,
        /** Topics already discussed today (to avoid repetition). */
        val topicsDiscussedToday: List<String> = emptyList(),
        /** Minutes since last proactive speak (cooldown). */
        val minutesSinceLastProactiveSpeak: Long? = null,
        /** Active autonomous goals (INTENT category), injected by the app. */
        val activeIntents: List<String> = emptyList(),
        /** Recent contextual observations (OBSERVATION category), injected by the app. */
        val recentObservations: List<String> = emptyList(),
        /** Emerging behavior patterns (PATTERN category), injected by the app. */
        val activePatterns: List<String> = emptyList(),
        /** Short habit profile from daily activity log (last 7 days). */
        val habitProfileSummary: String? = null,
        /** Recent ephemeral activities (today / yesterday). */
        val recentDailyActivities: List<String> = emptyList(),
        /** Current room label at desk (spatial memory). */
        val currentPlaceLabel: String? = null,
        /** Confidence for current room (0.0–1.0). */
        val placeConfidence: Float? = null,
        /** Known memorized places (short list). */
        val knownPlaces: List<String> = emptyList(),
        /** Active attention domain for this tick (round-robin). */
        val activeDomainId: String? = null,
        val activeDomainName: String? = null,
        /** Custom domain user prompt; built-in domains use asset files. */
        val activeDomainUserPrompt: String? = null,
        /** Recent proactive interventions on the active domain. */
        val recentInterventionsOnDomain: List<String> = emptyList(),
        /** ML Kit desk occupancy for this tick. */
        val deskOccupancyState: String? = null,
        /** Environment sensing freshness block (metadata only). */
        val environmentFreshnessBlock: String? = null,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : RobotInput() {
        override val sourceId: String = "heartbeat"
        override val priority: InputPriority = InputPriority.DEFERRED
    }

    /**
     * Weekly self-reflection trigger.
     * The robot analyzes its behavior and learns what works.
     */
    data class WeeklyReflection(
        /** Total user interactions this week. */
        val totalInteractions: Int,
        /** Total proactive speaks (heartbeat interventions). */
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
