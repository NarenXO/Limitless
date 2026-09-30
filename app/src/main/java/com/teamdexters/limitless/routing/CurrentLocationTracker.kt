package com.teamdexters.limitless.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CurrentLocationTracker @Inject constructor() {
    private val _currentRoomId = MutableStateFlow<String?>(null)
    val currentRoomId: StateFlow<String?> = _currentRoomId.asStateFlow()

    fun updateLocation(roomId: String?) {
        _currentRoomId.value = roomId
    }
    
    fun clear() {
        _currentRoomId.value = null
    }
}
