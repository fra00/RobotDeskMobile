package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.heartbeat.ProactiveInterventionRepository
import com.example.mydeskrobot.data.reflection.WeeklyStatsRepository
import com.example.mydeskrobot.data.workingmemory.WorkingMemoryRepository
import com.example.mydeskrobot.domain.heartbeat.InterventionOutcome
import com.example.mydeskrobot.domain.heartbeat.ProactiveIntervention
import com.example.mydeskrobot.domain.mood.MoodManager
import com.example.mydeskrobot.domain.mood.MoodTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ProactiveTracker(
    private val workingMemoryRepository: WorkingMemoryRepository,
    private val weeklyStatsRepository: WeeklyStatsRepository,
    private val interventionRepository: ProactiveInterventionRepository,
    private val moodManager: MoodManager?,
    private val scope: CoroutineScope,
) {
    private var lastProactiveSpeakTime: Long? = null
    private var lastProactiveTopic: String? = null

    fun markProactiveSpeak(topic: String, timestamp: Long = System.currentTimeMillis()) {
        lastProactiveSpeakTime = timestamp
        lastProactiveTopic = topic
    }

    suspend fun recordSpeak(domainId: String, text: String, topic: String) {
        workingMemoryRepository.recordProactiveSpeak()
        weeklyStatsRepository.recordProactiveSpeak()
        interventionRepository.append(
            ProactiveIntervention(
                domainId = domainId,
                topic = topic,
                text = text,
                outcome = InterventionOutcome.SPOKE,
            ),
        )
        markProactiveSpeak(topic)
    }

    suspend fun recordSuppressed(domainId: String, reason: String) {
        interventionRepository.append(
            ProactiveIntervention(
                domainId = domainId,
                topic = reason,
                text = "",
                outcome = InterventionOutcome.SUPPRESSED,
            ),
        )
        moodManager?.onTrigger(MoodTrigger.HeartbeatSuppressed)
    }

    suspend fun recordSilent(domainId: String) {
        interventionRepository.append(
            ProactiveIntervention(
                domainId = domainId,
                topic = "",
                text = "",
                outcome = InterventionOutcome.SILENT,
            ),
        )
    }

    fun checkProactiveResponse(userPhrase: String, now: Long = System.currentTimeMillis()) {
        if (userPhrase.isBlank()) return
        val lastSpeak = lastProactiveSpeakTime ?: return
        if (now - lastSpeak > RESPONSE_WINDOW_MS) return

        val topic = lastProactiveTopic
        lastProactiveSpeakTime = null
        lastProactiveTopic = null

        scope.launch {
            weeklyStatsRepository.recordPositiveResponse(topic)
            if (topic != null) {
                interventionRepository.append(
                    ProactiveIntervention(
                        domainId = "heartbeat",
                        topic = topic,
                        text = userPhrase,
                        outcome = InterventionOutcome.POSITIVE_RESPONSE,
                    ),
                )
            }
        }
    }

    suspend fun recordIgnoredIfTimedOut(now: Long = System.currentTimeMillis()) {
        val lastSpeak = lastProactiveSpeakTime ?: return
        if (now - lastSpeak <= RESPONSE_WINDOW_MS) return

        workingMemoryRepository.recordIgnoredSuggestion()
        weeklyStatsRepository.recordIgnoredSuggestion(lastProactiveTopic)
        interventionRepository.append(
            ProactiveIntervention(
                domainId = "heartbeat",
                topic = lastProactiveTopic.orEmpty(),
                text = "",
                outcome = InterventionOutcome.IGNORED,
            ),
        )
        lastProactiveSpeakTime = null
        lastProactiveTopic = null
    }

    private fun isPositiveResponse(phrase: String): Boolean {
        val lower = phrase.lowercase()
        return lower.contains("grazie") ||
            lower.contains("ok") ||
            lower.contains("bene") ||
            lower.contains("sì") ||
            lower.contains("si")
    }

    companion object {
        const val RESPONSE_WINDOW_MS = 5 * 60 * 1000L
    }
}
