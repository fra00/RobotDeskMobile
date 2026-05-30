package com.example.mydeskrobot.reasoning.model

/**
 * Wrapper for a [RobotInput] ready to be sent to the LLM.
 * Contains the normalized input, formatted text for the prompt, and dedup key.
 */
data class SystemInputEnvelope(
    val input: RobotInput,
    val formattedForLlm: String,
    val dedupKey: String,
) {
    companion object {
        /**
         * Creates an envelope from a [RobotInput.Notification].
         */
        fun fromNotification(notification: RobotInput.Notification): SystemInputEnvelope {
            val formatted = buildString {
                append("[SYSTEM_INPUT: notification]\n")
                append("App: ${notification.appLabel}\n")
                notification.title?.let { append("Titolo: $it\n") }
                notification.text?.let { append("Testo: $it\n") }
            }.trimEnd()

            val dedupKey = "notif:${notification.packageName}:${notification.notificationKey}"

            return SystemInputEnvelope(
                input = notification,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        /**
         * Creates an envelope from a [RobotInput.HardwareButton].
         */
        fun fromHardwareButton(button: RobotInput.HardwareButton): SystemInputEnvelope {
            val formatted = buildString {
                append("[SYSTEM_INPUT: hardware_button]\n")
                append("Button: ${button.buttonId}\n")
                append("Action: ${button.action}\n")
                if (button.payload.isNotEmpty()) {
                    append("Payload: ${button.payload}\n")
                }
            }.trimEnd()

            val dedupKey = "btn:${button.buttonId}:${button.action}:${button.timestamp}"

            return SystemInputEnvelope(
                input = button,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        /**
         * Creates an envelope from a [RobotInput.SensorReading].
         */
        fun fromSensorReading(sensor: RobotInput.SensorReading): SystemInputEnvelope {
            val formatted = buildString {
                append("[SYSTEM_INPUT: sensor]\n")
                append("Tipo: ${sensor.sensorType}\n")
                append("Valore: ${sensor.value} ${sensor.unit}\n")
                if (sensor.thresholdCrossed) {
                    append("Soglia superata: sì\n")
                }
            }.trimEnd()

            val dedupKey = "sensor:${sensor.sensorType}:${sensor.timestamp / 60000}"

            return SystemInputEnvelope(
                input = sensor,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        fun fromScheduledTask(task: RobotInput.ScheduledTaskFired): SystemInputEnvelope {
            val timeLabel = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(task.triggerAtMillis))
            val formatted = buildString {
                append("[SYSTEM_INPUT: scheduled_task]\n")
                append("Id: ${task.taskId}\n")
                append("Messaggio: ${task.message}\n")
                append("Scadenza: $timeLabel\n")
            }.trimEnd()

            val dedupKey = "task:${task.taskId}:${task.triggerAtMillis}"

            return SystemInputEnvelope(
                input = task,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        /**
         * Factory method that dispatches to the appropriate builder.
         */
        fun from(input: RobotInput): SystemInputEnvelope = when (input) {
            is RobotInput.Notification -> fromNotification(input)
            is RobotInput.HardwareButton -> fromHardwareButton(input)
            is RobotInput.SensorReading -> fromSensorReading(input)
            is RobotInput.ScheduledTaskFired -> fromScheduledTask(input)
        }
    }
}
