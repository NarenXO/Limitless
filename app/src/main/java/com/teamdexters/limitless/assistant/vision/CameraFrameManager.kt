package com.teamdexters.limitless.assistant.vision

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CameraFrameManager {
    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame

    fun updateFrame(bitmap: Bitmap?) {
        _latestFrame.value = bitmap
    }
    
    fun getFrame(): Bitmap? {
        return _latestFrame.value
    }
}
