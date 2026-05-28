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
}
