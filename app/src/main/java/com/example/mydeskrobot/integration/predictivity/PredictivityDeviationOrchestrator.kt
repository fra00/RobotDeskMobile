package com.example.mydeskrobot.integration.predictivity

import com.example.mydeskrobot.domain.input.SystemInputDispatcher
import com.example.mydeskrobot.domain.input.SystemInputEvent
import com.example.mydeskrobot.domain.predictivity.HabitSlot
import com.example.mydeskrobot.reasoning.model.RobotInput
import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

class PredictivityDeviationOrchestrator {

    fun buildEnvelope(slot: HabitSlot): SystemInputEnvelope {
        val input = RobotInput.PredictivityDeviation(
            slotKey = slot.slotKey,
            displayLabel = slot.displayLabel,
            typicalTimeMinutes = slot.typicalTimeMinutes,
            hitCount = slot.hitCount,
            confidence = slot.confidence,
        )
        return SystemInputEnvelope.fromPredictivityDeviation(input)
    }

    fun dispatch(slot: HabitSlot) {
        val envelope = buildEnvelope(slot)
        SystemInputDispatcher.emit(SystemInputEvent.InputReceived(envelope))
    }
}
