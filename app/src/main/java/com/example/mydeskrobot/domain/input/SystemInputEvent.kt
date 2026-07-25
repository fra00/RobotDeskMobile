package com.example.mydeskrobot.domain.input

import com.example.mydeskrobot.reasoning.model.SystemInputEnvelope

/**
 * Events emitted by the input system to the ViewModel.
 */
sealed interface SystemInputEvent {
    /**
     * A system input is ready to be processed.
     */
    data class InputReceived(val envelope: SystemInputEnvelope) : SystemInputEvent
}
