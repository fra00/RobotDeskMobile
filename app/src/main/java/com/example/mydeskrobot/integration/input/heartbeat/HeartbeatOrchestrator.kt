package com.example.mydeskrobot.integration.input.heartbeat

import android.content.Context
import android.util.Log
import com.example.mydeskrobot.data.activitylog.ActivityLogRepository
import com.example.mydeskrobot.data.awareness.UserAwarenessRepository
import com.example.mydeskrobot.data.context.RobotContextRepository
import com.example.mydeskrobot.data.heartbeat.AttentionDomainRepository
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettingsRepository
import com.example.mydeskrobot.data.heartbeat.ProactiveInterventionRepository
import com.example.mydeskrobot.data.heartbeat.SensingKind
import com.example.mydeskrobot.data.heartbeat.SensingLogRepository
import com.example.mydeskrobot.data.mood.MoodRepository
import com.example.mydeskrobot.data.presence.DeskPresenceStateStore
import com.example.mydeskrobot.data.scheduled.ScheduledTaskRepository
import com.example.mydeskrobot.data.spatial.SpatialContextRepository
import com.example.mydeskrobot.data.spatial.SpatialPlaceRepository
import com.example.mydeskrobot.data.workingmemory.WorkingMemoryRepository
import com.example.mydeskrobot.domain.heartbeat.AttentionDomainState
import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.data.hotword.VoiceSessionState
import com.example.mydeskrobot.data.presence.DeskPresenceSettingsRepository
import com.example.mydeskrobot.domain.heartbeat.HeartbeatMicroTickPolicy
import com.example.mydeskrobot.domain.input.HeartbeatMicroTick
import com.example.mydeskrobot.domain.model.RobotEmotion
import com.example.mydeskrobot.domain.presence.DeskPresenceGate
import com.example.mydeskrobot.memory.unified.UnifiedMemoryFactory
import com.example.mydeskrobot.reasoning.model.RobotInput
import kotlinx.coroutines.runBlocking

/**
 * SSOT for heartbeat tick lifecycle: gates, domain selection, context build, dispatch.
 */
class HeartbeatOrchestrator(
    private val context: Context,
    private val settingsRepository: HeartbeatSettingsRepository,
    private val gatePolicy: ProactiveGatePolicy,
    private val domainScheduler: DomainScheduler,
    private val domainRepository: AttentionDomainRepository,
    private val interventionRepository: ProactiveInterventionRepository,
    private val sensingLogRepository: SensingLogRepository,
    private val environmentFreshnessProvider: EnvironmentFreshnessProvider,
    private val eventBus: DomainEventBus,
    private val deskPresenceSettingsRepository: DeskPresenceSettingsRepository,
) {
    fun onAlarmTick() {
        val settings = runBlocking { settingsRepository.load() }
        runTick(HeartbeatTickSource.ALARM, settings)
        HeartbeatScheduler.rescheduleNext(context, settings.intervalMinutes)
    }

    /**
     * Voice command entry: same proactive gates and LLM pipeline as [onAlarmTick].
     * Only scheduling is relaxed when no domain is currently due.
     */
    fun triggerVoiceHeartbeat(): VoiceHeartbeatTriggerResult {
        val settings = runBlocking { settingsRepository.load() }
        when (val decision = runBlocking { evaluateProactiveGates(settings) }) {
            is GateDecision.Skip -> {
                Log.d(TAG, "Voice heartbeat blocked: ${decision.reason}")
                return VoiceHeartbeatTriggerResult.GateBlocked(decision.reason)
            }
            GateDecision.Proceed -> Unit
        }

        val activeDomain = runBlocking { selectDomainForVoice() }
            ?: return VoiceHeartbeatTriggerResult.NoEnabledDomains

        runBlocking {
            unifiedMemoryPrune()
            buildAndDispatch(
                settings = settings,
                activeDomain = activeDomain,
                dedupKeyOverride = "heartbeat:voice:${System.currentTimeMillis()}",
            )
            domainRepository.updateLastChecked(activeDomain.id, System.currentTimeMillis())
            sensingLogRepository.record(
                SensingKind.PRESENCE_ML,
                outcome = DeskPresenceStateStore.current().state.name.lowercase(),
            )
        }

        Log.i(TAG, "Voice-triggered heartbeat domain=${activeDomain.id}")
        return VoiceHeartbeatTriggerResult.Dispatched(activeDomain.displayName)
    }

    private fun runTick(source: HeartbeatTickSource, settings: com.example.mydeskrobot.data.heartbeat.HeartbeatSettings) {
        Log.d(TAG, "Heartbeat tick source=$source")

        when (val decision = runBlocking { evaluateProactiveGates(settings) }) {
            is GateDecision.Skip -> {
                Log.d(TAG, "Heartbeat tick skipped: ${decision.reason}")
                return
            }
            GateDecision.Proceed -> Unit
        }

        val activeDomain = runBlocking {
            domainScheduler.nextDueDomain(domainRepository.enabledDomains())
        }

        if (activeDomain == null) {
            if (source == HeartbeatTickSource.ALARM) {
                runBlocking { tryMicroTick(settings) }
            }
            return
        }

        runBlocking {
            unifiedMemoryPrune()
            buildAndDispatch(settings = settings, activeDomain = activeDomain)
            domainRepository.updateLastChecked(activeDomain.id, System.currentTimeMillis())
            sensingLogRepository.record(
                SensingKind.PRESENCE_ML,
                outcome = DeskPresenceStateStore.current().state.name.lowercase(),
            )
        }
    }

    private suspend fun evaluateProactiveGates(
        settings: com.example.mydeskrobot.data.heartbeat.HeartbeatSettings,
    ): GateDecision {
        val workingMemoryRepo = WorkingMemoryRepository(context)
        val userAwarenessRepo = UserAwarenessRepository(context)
        val robotContextRepo = RobotContextRepository(context)
        val gateContext = ProactiveGateContext(
            heartbeatSettings = settings,
            workingMemory = workingMemoryRepo.load(),
            userAwareness = userAwarenessRepo.load(),
            robotContext = robotContextRepo.getStoredState(),
            isNightMode = false,
            deskPresenceMonitorEnabled = true,
        )
        return gatePolicy.shouldRunTick(gateContext)
    }

    private suspend fun selectDomainForVoice(): AttentionDomainState? {
        val enabled = domainRepository.enabledDomains()
        return domainScheduler.nextDueDomain(enabled)
            ?: domainScheduler.nextDomainForDebug(enabled)
    }

    fun onDomainEvent(eventId: String) {
        eventBus.fire(eventId)
    }

    fun onDomainsChanged() {
        domainScheduler.resetRoundRobin()
    }

    private suspend fun tryMicroTick(settings: com.example.mydeskrobot.data.heartbeat.HeartbeatSettings) {
        val moodRepo = MoodRepository(context)
        val mood = moodRepo.load()
        val idleMinutes = if (settings.lastInteractionMillis > 0L) {
            (System.currentTimeMillis() - settings.lastInteractionMillis) / 60_000L
        } else {
            0L
        }
        val deskSettings = deskPresenceSettingsRepository.load()
        val occupancy = DeskPresenceStateStore.current()
        val presenceAllows = DeskPresenceGate.allowsProactiveInteraction(
            occupancy = occupancy,
            lastInteractionMillis = settings.lastInteractionMillis,
            monitorEnabled = deskSettings.enabled,
        )
        val moodEmotion = mood.baseEmotion
        if (!HeartbeatMicroTickPolicy.shouldRun(
                moodEmotion = moodEmotion,
                idleMinutes = idleMinutes,
                presenceAllows = presenceAllows,
                voiceSessionActive = VoiceSessionState.isActive,
            )
        ) {
            Log.d(TAG, "Micro tick skipped (mood=$moodEmotion idle=${idleMinutes}m)")
            return
        }

        val suggestBody = moodEmotion == RobotEmotion.BORED || moodEmotion == RobotEmotion.DROWSY
        val tick = HeartbeatMicroTick(
            idleMinutes = idleMinutes,
            moodLabel = moodEmotion?.name?.lowercase(),
            suggestBodyLookAround = suggestBody,
        )
        Log.i(TAG, "Emitting heartbeat MICRO tick (idle=${idleMinutes}m)")
        SystemInputDispatcher.emit(SystemInputEvent.MicroTick(tick))
        sensingLogRepository.record(SensingKind.LOOK_AROUND)
    }

    private suspend fun unifiedMemoryPrune() {
        val unifiedMemoryRepo = UnifiedMemoryFactory.createRepository(context)
        val activityLogRepo = ActivityLogRepository.create(context)
        unifiedMemoryRepo.pruneExpired()
        activityLogRepo.pruneExpired()
    }

    private suspend fun buildAndDispatch(
        settings: com.example.mydeskrobot.data.heartbeat.HeartbeatSettings,
        activeDomain: AttentionDomainState,
        dedupKeyOverride: String? = null,
    ) {
        val scheduledTaskRepo = ScheduledTaskRepository.create(context)
        val unifiedMemoryRepo = UnifiedMemoryFactory.createRepository(context)
        val moodRepo = MoodRepository(context)
        val workingMemoryRepo = WorkingMemoryRepository(context)
        val userAwarenessRepo = UserAwarenessRepository(context)
        val activityLogRepo = ActivityLogRepository.create(context)
        val spatialContextRepo = SpatialContextRepository(context)
        val spatialPlaceRepo = SpatialPlaceRepository.create(context)

        val contextBuilder = HeartbeatContextBuilder(
            scheduledTaskRepository = scheduledTaskRepo,
            unifiedMemoryRepository = unifiedMemoryRepo,
            lastInteractionProvider = { settings.lastInteractionMillis },
            currentMoodProvider = { moodRepo.load() },
            workingMemoryProvider = { workingMemoryRepo.load() },
            userAwarenessProvider = { userAwarenessRepo.load() },
            activityLogRepository = activityLogRepo,
            spatialSnapshotProvider = { spatialContextRepo.load() },
            knownPlacesProvider = { spatialPlaceRepo.labelSummaries() },
        )

        val baseHeartbeat = contextBuilder.build()
        val interventions = interventionRepository.recentForDomain(activeDomain.id)
            .map { "${it.outcome.name}: ${it.text.take(80)}" }

        val occupancy = DeskPresenceStateStore.current()
        val heartbeat = baseHeartbeat.copy(
            activeDomainId = activeDomain.id,
            activeDomainName = activeDomain.displayName,
            activeDomainSensitivity = activeDomain.sensitivity.name,
            activeDomainUserPrompt = activeDomain.userPrompt,
            recentInterventionsOnDomain = interventions,
            deskOccupancyState = occupancy.state.name.lowercase(),
            environmentFreshnessBlock = environmentFreshnessProvider.buildBlock(),
        )

        val inputSource = HeartbeatInputSource()
        val envelope = inputSource.toEnvelope(heartbeat).let { env ->
            if (dedupKeyOverride != null) env.copy(dedupKey = dedupKeyOverride) else env
        }
        Log.i(TAG, "Dispatching heartbeat domain=${activeDomain.id}")
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
    }

    companion object {
        private const val TAG = "HeartbeatOrchestrator"
    }
}
