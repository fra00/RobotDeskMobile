package com.example.mydeskrobot.domain.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus for system input events between InputRouter and ViewModel.
 * Similar to [com.example.mydeskrobot.domain.hotword.HotwordEventDispatcher].
 */
object SystemInputDispatcher {

    private val _events = MutableSharedFlow<SystemInputEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SystemInputEvent> = _events.asSharedFlow()

    fun emit(event: SystemInputEvent) {
        _events.tryEmit(event)
    }
}
