package com.teamdexters.limitless.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PowerTriggerBus {
    private val _triggerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val triggerEvent = _triggerEvent.asSharedFlow()

    fun emitTrigger() {
        _triggerEvent.tryEmit(Unit)
    }
}
