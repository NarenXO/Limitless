package com.teamdexters.limitless.assistant

/**
 * Interface for detecting wake words to activate Hazel assistant.
 * Currently implemented as a stub for future TFLite / Teachable Machine integration.
 */
interface WakeWordListener {
    
    /**
     * Starts listening for the wake word "Hey Hazel".
     * @param onWakeWordDetected Callback function to execute when wake word is detected
     */
    fun startListening(onWakeWordDetected: () -> Unit)
    
    /**
     * Stops listening for the wake word.
     * Should be called when not needed to conserve resources.
     */
    fun stopListening()
}

/**
 * Default implementation of WakeWordListener as a stub.
 * TODO: Real TFLite / Teachable Machine keyword spotter will be integrated here.
 * The actual implementation will use TensorFlow Lite or Teachable Machine
 * for efficient on-device wake word detection.
 */
class DefaultWakeWordListener : WakeWordListener {
    
    private var isListening = false
    private var onWakeWordDetected: (() -> Unit)? = null
    
    override fun startListening(onWakeWordDetected: () -> Unit) {
        this.onWakeWordDetected = onWakeWordDetected
        isListening = true
        
        // TODO: Integrate TFLite / Teachable Machine keyword spotter here
        // For now, this is a stub that would need:
        // 1. TFLite model for wake word detection
        // 2. Audio input stream configuration
        // 3. Real-time audio processing
        // 4. Threshold-based detection logic
    }
    
    override fun stopListening() {
        isListening = false
        onWakeWordDetected = null
        
        // TODO: Clean up TFLite resources and audio streams
    }
    
    /**
     * Checks if the listener is currently active.
     * @return true if listening, false otherwise
     */
    fun isActive(): Boolean = isListening
}