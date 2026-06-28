package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.data.heartbeat.SensingLogRepository
import com.example.mydeskrobot.data.presence.DeskPresenceStateStore
import com.example.mydeskrobot.domain.presence.DeskOccupancy
import com.example.mydeskrobot.domain.presence.DeskOccupancyState
import com.example.mydeskrobot.domain.presence.DeskPresenceGate
import com.example.mydeskrobot.domain.spatial.SpatialContextSnapshot
import java.util.concurrent.TimeUnit

class EnvironmentFreshnessProvider(
    private val sensingLogRepository: SensingLogRepository,
    private val spatialSnapshotProvider: suspend () -> SpatialContextSnapshot?,
) {
    suspend fun buildBlock(now: Long = System.currentTimeMillis()): String {
        val occupancy = DeskPresenceStateStore.current()
        val lastMl = sensingLogRepository.lastAt(com.example.mydeskrobot.data.heartbeat.SensingKind.PRESENCE_ML)
        val lastLlm = sensingLogRepository.lastAt(com.example.mydeskrobot.data.heartbeat.SensingKind.PRESENCE_LLM)
        val lastRoom = sensingLogRepository.lastAt(com.example.mydeskrobot.data.heartbeat.SensingKind.ROOM_SCENE)
        val spatial = spatialSnapshotProvider()

        return buildString {
            appendLine("OSSERVAZIONE AMBIENTE (metadati):")
            appendLine("- Presenza ML Kit: ${formatOccupancy(occupancy)} (${ageLabel(lastMl, now)})")
            appendLine("- Ultima verifica presenza LLM: ${ageLabel(lastLlm, now)}")
            appendLine("- Ultima foto scena stanza: ${ageLabel(lastRoom, now)}")
            spatial?.let {
                appendLine("- Stanza corrente: ${it.currentPlaceLabel ?: "sconosciuta"} (conf ${it.confidence})")
            }
        }.trimEnd()
    }

    private fun formatOccupancy(occupancy: DeskOccupancy): String =
        when (occupancy.state) {
            DeskOccupancyState.PRESENT -> "present"
            DeskOccupancyState.ABSENT -> "absent"
            DeskOccupancyState.UNCERTAIN -> "uncertain"
            DeskOccupancyState.UNKNOWN -> "unknown"
        }

    private fun ageLabel(timestamp: Long?, now: Long): String {
        if (timestamp == null || timestamp <= 0L) return "mai"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(now - timestamp)
        return when {
            minutes < 1 -> "adesso"
            minutes < 60 -> "$minutes min fa"
            else -> "${minutes / 60} h fa"
        }
    }
}
