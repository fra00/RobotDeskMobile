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
         * Creates an envelope from a [RobotInput.Heartbeat].
         */
        fun fromHeartbeat(heartbeat: RobotInput.Heartbeat): SystemInputEnvelope {
            val timeLabel = "${heartbeat.currentHour}:${heartbeat.currentMinute.toString().padStart(2, '0')}"
            val formatted = buildString {
                append("[SYSTEM_INPUT: heartbeat]\n")
                append("Ora: $timeLabel\n")
                append("Giorno: ${heartbeat.dayOfWeek}\n")
                append("Minuti dall'ultima interazione: ${heartbeat.minutesSinceLastInteraction}\n")
                if (heartbeat.moodValence != null || heartbeat.moodLabel != null) {
                    heartbeat.moodValence?.let { v ->
                        val sign = if (v >= 0f) "+" else ""
                        append("Valenza benessere: $sign${"%.2f".format(v)}\n")
                    }
                    if (heartbeat.moodLabel != null) {
                        val intensityPct = heartbeat.moodIntensity?.let { (it * 100).toInt() } ?: 50
                        append("Stato emotivo: ${heartbeat.moodLabel} ($intensityPct%)\n")
                    }
                }
                if (heartbeat.todayInteractions > 0) {
                    append("Interazioni oggi: ${heartbeat.todayInteractions}\n")
                }
                if (heartbeat.proactiveSpeaksToday > 0) {
                    append("Interventi proattivi oggi: ${heartbeat.proactiveSpeaksToday}\n")
                }
                heartbeat.minutesSinceLastProactiveSpeak?.let { mins ->
                    append("Minuti dall'ultimo intervento: $mins\n")
                }
                if (heartbeat.topicsDiscussedToday.isNotEmpty()) {
                    append("Topic già discussi oggi: ${heartbeat.topicsDiscussedToday.joinToString(", ")}\n")
                }
                if (heartbeat.pendingRemindersCount > 0) {
                    append("Promemoria attivi: ${heartbeat.pendingRemindersCount}\n")
                }
                heartbeat.relevantRoutines.forEach { routine ->
                    append("Routine: $routine\n")
                }
                if (heartbeat.activeIntents.isNotEmpty()) {
                    append("OBIETTIVI ATTIVI (INTENT):\n")
                    heartbeat.activeIntents.forEach { intent ->
                        append("- $intent\n")
                    }
                }
                if (heartbeat.recentObservations.isNotEmpty()) {
                    append("OSSERVAZIONI RECENTI (OBSERVATION):\n")
                    heartbeat.recentObservations.forEach { observation ->
                        append("- $observation\n")
                    }
                }
                if (heartbeat.activePatterns.isNotEmpty()) {
                    append("PATTERN EMERGENTI (PATTERN):\n")
                    heartbeat.activePatterns.forEach { pattern ->
                        append("- $pattern\n")
                    }
                }
                if (!heartbeat.habitProfileSummary.isNullOrBlank()) {
                    append("PROFILO ABITUDINI:\n")
                    append(heartbeat.habitProfileSummary.trim())
                    append('\n')
                }
                if (heartbeat.recentDailyActivities.isNotEmpty()) {
                    append("ATTIVITÀ RECENTI:\n")
                    heartbeat.recentDailyActivities.forEach { activity ->
                        append("- $activity\n")
                    }
                }
                if (heartbeat.currentPlaceLabel != null) {
                    val conf = heartbeat.placeConfidence?.let { " (confidenza ${"%.2f".format(it)})" }.orEmpty()
                    append("Stanza corrente: ${heartbeat.currentPlaceLabel}$conf\n")
                } else {
                    append("Stanza corrente: sconosciuta\n")
                }
                if (heartbeat.knownPlaces.isNotEmpty()) {
                    append("Luoghi noti: ${heartbeat.knownPlaces.joinToString(", ")}\n")
                }
                if (heartbeat.activeDomainId != null) {
                    append("DOMINIO ATTIVO: ${heartbeat.activeDomainName ?: heartbeat.activeDomainId}\n")
                }
                if (heartbeat.recentInterventionsOnDomain.isNotEmpty()) {
                    append("Interventi recenti su questo dominio:\n")
                    heartbeat.recentInterventionsOnDomain.forEach { line ->
                        append("- $line\n")
                    }
                }
                heartbeat.deskOccupancyState?.let {
                    append("Presenza scrivania (ML Kit): $it\n")
                }
                if (!heartbeat.environmentFreshnessBlock.isNullOrBlank()) {
                    append(heartbeat.environmentFreshnessBlock.trim())
                    append('\n')
                }
            }.trimEnd()

            val dedupKey = "heartbeat:${heartbeat.timestamp / 60000}"

            return SystemInputEnvelope(
                input = heartbeat,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        /**
         * Creates an envelope from a [RobotInput.WeeklyReflection].
         */
        fun fromWeeklyReflection(reflection: RobotInput.WeeklyReflection): SystemInputEnvelope {
            val formatted = buildString {
                append("[SYSTEM_INPUT: weekly_reflection]\n")
                append("Questa settimana:\n")
                append("- Interazioni utente: ${reflection.totalInteractions}\n")
                append("- Interventi proattivi: ${reflection.totalProactiveSpeaks}\n")
                append("- Risposte positive: ${reflection.positiveResponses}\n")
                append("- Ignorati/rifiutati: ${reflection.ignoredSuggestions}\n")
                append("- Tasso di successo: ${reflection.successRatePercent}%\n")
                if (reflection.usefulTopics.isNotEmpty()) {
                    append("- Topic utili: ${reflection.usefulTopics.joinToString(", ")}\n")
                }
                if (reflection.ignoredTopics.isNotEmpty()) {
                    append("- Topic ignorati: ${reflection.ignoredTopics.joinToString(", ")}\n")
                }
            }.trimEnd()

            val dedupKey = "reflection:${reflection.timestamp}"

            return SystemInputEnvelope(
                input = reflection,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        fun fromPredictivityDeviation(deviation: RobotInput.PredictivityDeviation): SystemInputEnvelope {
            val hour = deviation.typicalTimeMinutes / 60
            val minute = deviation.typicalTimeMinutes % 60
            val timeLabel = "${hour}:${minute.toString().padStart(2, '0')}"
            val formatted = buildString {
                append("[SYSTEM_INPUT: predictivity_deviation]\n")
                append("Attività abituale: ${deviation.displayLabel}\n")
                append("Orario tipico: $timeLabel\n")
                append("Occorrenze osservate: ${deviation.hitCount}\n")
                append("Confidenza abitudine: ${"%.0f".format(deviation.confidence * 100)}%\n")
                append("Oggi non risulta nel log attività entro la finestra abituale.\n")
            }.trimEnd()

            val dedupKey = "predictivity_deviation:${deviation.slotKey}:${deviation.timestamp / 60000}"

            return SystemInputEnvelope(
                input = deviation,
                formattedForLlm = formatted,
                dedupKey = dedupKey,
            )
        }

        fun fromWellnessCheck(check: RobotInput.WellnessCheck): SystemInputEnvelope {
            val phaseLabel = when (check.phase) {
                com.example.mydeskrobot.domain.wellness.WellnessPhase.VISUAL_ORDER ->
                    "visual_order (silent body scan + room order photo)"
                com.example.mydeskrobot.domain.wellness.WellnessPhase.DOMAIN_SCORE ->
                    "domain_score (max one short sentence if needed)"
            }
            val enabledLabels = check.enabledDomainIds
                .sorted()
                .map { id ->
                    check.customDomains.find { it.id == id }?.displayName
                        ?: com.example.mydeskrobot.domain.wellness.WellnessDomains.DISPLAY_NAMES[id]
                        ?: id
                }
            val formatted = buildString {
                append("[SYSTEM_INPUT: wellness_check]\n")
                append("Phase: $phaseLabel\n")
                if (enabledLabels.isNotEmpty()) {
                    append("Enabled domains: ${enabledLabels.joinToString(", ")}\n")
                } else {
                    append("Enabled domains: (none)\n")
                }
                if (check.customDomains.isNotEmpty()) {
                    append("Custom domain prompts:\n")
                    check.customDomains.forEach { custom ->
                        append("- ${custom.displayName}: ${custom.prompt}\n")
                    }
                }
                if (check.bodyConfigured && check.bodyReachable) {
                    append("Body: configured and reachable\n")
                } else {
                    append("Body: not available (skip visual order)\n")
                }
                check.habitProfileSummary?.takeIf { it.isNotBlank() }?.let {
                    append("Habit summary:\n$it\n")
                }
                if (check.recentDailyActivities.isNotEmpty()) {
                    append("Today activities:\n")
                    check.recentDailyActivities.forEach { append("- $it\n") }
                }
                if (check.activePatterns.isNotEmpty()) {
                    append("Patterns:\n")
                    check.activePatterns.take(5).forEach { append("- $it\n") }
                }
                if (check.recentObservations.isNotEmpty()) {
                    append("Recent observations:\n")
                    check.recentObservations.take(5).forEach { append("- $it\n") }
                }
                check.orderObservationFresh?.let {
                    append("Fresh order observation:\n$it\n")
                }
            }.trimEnd()

            val dedupKey = "wellness_check:${check.phase.name}:${check.timestamp / 60000}"

            return SystemInputEnvelope(
                input = check,
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
            is RobotInput.Heartbeat -> fromHeartbeat(input)
            is RobotInput.WeeklyReflection -> fromWeeklyReflection(input)
            is RobotInput.PredictivityDeviation -> fromPredictivityDeviation(input)
            is RobotInput.WellnessCheck -> fromWellnessCheck(input)
        }
    }
}
