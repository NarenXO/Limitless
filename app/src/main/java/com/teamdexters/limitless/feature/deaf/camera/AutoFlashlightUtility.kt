package com.teamdexters.limitless.feature.deaf.camera

import androidx.camera.core.ImageProxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auto-Flashlight Utility for CameraX.
 * 
 * Analyzes incoming CameraX ImageProxy frames using YUV Y-plane average pixel brightness calculation.
 * If average brightness falls below threshold, automatically triggers torch activation.
 * 
 * Threshold: average luminance < 35 out of 255 (configurable)
 */
class AutoFlashlightUtility(
    private var brightnessThreshold: Int = 35
) {
    
    private val _isLowLight = MutableStateFlow(false)
    val isLowLight: StateFlow<Boolean> = _isLowLight.asStateFlow()
    
    private val _averageBrightness = MutableStateFlow(0)
    val averageBrightness: StateFlow<Int> = _averageBrightness.asStateFlow()
    
    /**
     * Analyze camera frame for luminance.
     * Returns true if torch should be enabled (low light detected).
     */
    fun analyzeFrame(imageProxy: ImageProxy): Boolean {
        val brightness = calculateAverageBrightness(imageProxy)
        _averageBrightness.value = brightness
        
        val isLow = brightness < brightnessThreshold
        _isLowLight.value = isLow
        
        return isLow
    }
    
    /**
     * Calculate average brightness from YUV Y-plane.
     * The Y plane in YUV represents luminance (brightness).
     */
    private fun calculateAverageBrightness(imageProxy: ImageProxy): Int {
        val buffer = imageProxy.planes[0].buffer
        val data = buffer.toByteArray()
        
        if (data.isEmpty()) {
            return 255
        }
        
        var sum = 0L
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }
        
        return (sum / data.size).toInt()
    }
    
    /**
     * Reset the low light state.
     */
    fun reset() {
        _isLowLight.value = false
        _averageBrightness.value = 0
    }
    
    /**
     * Update the brightness threshold.
     */
    fun setThreshold(threshold: Int) {
        require(threshold in 0..255) { "Threshold must be between 0 and 255" }
        brightnessThreshold = threshold
    }
}

/**
 * Extension function to convert ByteBuffer to ByteArray.
 */
private fun java.nio.ByteBuffer.toByteArray(): ByteArray {
    val byteArray = ByteArray(remaining())
    get(byteArray)
    return byteArray
}
