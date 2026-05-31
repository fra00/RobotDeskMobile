package com.example.mydeskrobot.integration.input.reflection

import com.example.mydeskrobot.integration.input.InputSource
import com.example.mydeskrobot.reasoning.model.InputPriority
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

/**
 * Formats [RobotInput.WeeklyReflection] for the system input bus.
 * Triggered once per week for the robot to reflect on its behavior.
 */
class ReflectionInputSource : InputSource {

    override val id: String = "weekly_reflection"
    override val priority: InputPriority = InputPriority.DEFERRED
    override val displayName: String = "Auto-riflessione"

    override fun isEnabled(): Boolean = true

    override fun normalize(raw: Any): RobotInput? {
        return raw as? RobotInput.WeeklyReflection
    }

    override fun shouldAccept(input: RobotInput): Boolean =
        input is RobotInput.WeeklyReflection

    override fun toEnvelope(input: RobotInput): SystemInputEnvelope {
        require(input is RobotInput.WeeklyReflection)
        return SystemInputEnvelope.fromWeeklyReflection(input)
    }

    override fun toDedupKey(input: RobotInput): String {
        require(input is RobotInput.WeeklyReflection)
        return "reflection:${input.timestamp}"
    }
}
