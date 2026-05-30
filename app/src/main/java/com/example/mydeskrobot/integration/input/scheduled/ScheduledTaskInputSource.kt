package com.example.mydeskrobot.integration.input.scheduled

import com.example.mydeskrobot.integration.input.InputSource
import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

/**
 * Formats [RobotInput.ScheduledTaskFired] for the system input bus.
 */
class ScheduledTaskInputSource : InputSource {

    override val id: String = "scheduled_task"
    override val priority: InputPriority = InputPriority.DEFERRED
    override val displayName: String = "Promemoria"

    override fun isEnabled(): Boolean = true

    override fun normalize(raw: Any): RobotInput? {
        return raw as? RobotInput.ScheduledTaskFired
    }

    override fun shouldAccept(input: RobotInput): Boolean =
        input is RobotInput.ScheduledTaskFired && input.message.isNotBlank()

    override fun toEnvelope(input: RobotInput): SystemInputEnvelope {
        require(input is RobotInput.ScheduledTaskFired)
        return SystemInputEnvelope.fromScheduledTask(input)
    }

    override fun toDedupKey(input: RobotInput): String {
        require(input is RobotInput.ScheduledTaskFired)
        return "task:${input.taskId}:${input.triggerAtMillis}"
    }
}
