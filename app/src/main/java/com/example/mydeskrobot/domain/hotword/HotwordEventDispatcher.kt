package com.example.mydeskrobot.domain.hotword

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bus leggero tra il foreground service (hotword) e il ViewModel.
 */
object HotwordEventDispatcher {

    private val _events = MutableSharedFlow<HotwordEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HotwordEvent> = _events.asSharedFlow()

    fun emit(event: HotwordEvent) {
        _events.tryEmit(event)
    }
}
