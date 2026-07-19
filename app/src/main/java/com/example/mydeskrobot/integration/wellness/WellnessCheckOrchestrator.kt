package com.example.mydeskrobot.integration.wellness

import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

class WellnessCheckOrchestrator {

    fun buildEnvelope(input: RobotInput.WellnessCheck): SystemInputEnvelope =
        SystemInputEnvelope.fromWellnessCheck(input)

    fun dispatch(input: RobotInput.WellnessCheck) {
        val envelope = buildEnvelope(input)
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
    }
}
