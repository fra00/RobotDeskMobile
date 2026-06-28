package com.example.mydeskrobot.integration.input.heartbeat

import android.content.Context
import com.example.mydeskrobot.data.heartbeat.AttentionDomainRepository
import com.example.mydeskrobot.data.heartbeat.HeartbeatSettingsRepository
import com.example.mydeskrobot.data.heartbeat.ProactiveInterventionRepository
import com.example.mydeskrobot.data.heartbeat.SensingLogRepository
import com.example.mydeskrobot.data.presence.DeskPresenceSettingsRepository
import com.example.mydeskrobot.data.spatial.SpatialContextRepository

object HeartbeatModule {
    private val eventBus = DomainEventBus()

    fun createOrchestrator(context: Context): HeartbeatOrchestrator {
        val appContext = context.applicationContext
        val sensingLogRepository = SensingLogRepository(appContext)
        val spatialContextRepo = SpatialContextRepository(appContext)

        return HeartbeatOrchestrator(
            context = appContext,
            settingsRepository = HeartbeatSettingsRepository(appContext),
            gatePolicy = ProactiveGatePolicy(DeskPresenceSettingsRepository(appContext)),
            domainScheduler = DomainScheduler(eventBus),
            domainRepository = AttentionDomainRepository(appContext),
            interventionRepository = ProactiveInterventionRepository(appContext),
            sensingLogRepository = sensingLogRepository,
            environmentFreshnessProvider = EnvironmentFreshnessProvider(
                sensingLogRepository = sensingLogRepository,
                spatialSnapshotProvider = { spatialContextRepo.load() },
            ),
            eventBus = eventBus,
            deskPresenceSettingsRepository = DeskPresenceSettingsRepository(appContext),
        )
    }

    fun domainEventBus(context: Context): DomainEventBus = eventBus
}
