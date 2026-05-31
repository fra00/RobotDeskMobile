package com.example.mydeskrobot.integration.input.heartbeat

import com.example.mydeskrobot.integration.input.InputSource
import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

/**
 * Formats [RobotInput.Heartbeat] for the system input bus.
 * Enablement is controlled upstream by HeartbeatScheduler (settings + time window).
 */
class HeartbeatInputSource : InputSource {

    override val id: String = "heartbeat"
    override val priority: InputPriority = InputPriority.DEFERRED
    override val displayName: String = "Proattività"

    override fun isEnabled(): Boolean = true

    override fun normalize(raw: Any): RobotInput? {
        return raw as? RobotInput.Heartbeat
    }

    override fun shouldAccept(input: RobotInput): Boolean =
        input is RobotInput.Heartbeat

    override fun toEnvelope(input: RobotInput): SystemInputEnvelope {
        require(input is RobotInput.Heartbeat)
        return SystemInputEnvelope.fromHeartbeat(input)
    }

    override fun toDedupKey(input: RobotInput): String {
        require(input is RobotInput.Heartbeat)
        return "heartbeat:${input.timestamp / 60000}"
    }
}
