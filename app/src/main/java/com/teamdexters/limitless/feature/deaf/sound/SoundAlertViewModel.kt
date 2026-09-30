package com.teamdexters.limitless.feature.deaf.sound

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing sound alerts and vibration patterns.
 */
class SoundAlertViewModel(private val context: Context) : ViewModel() {
    
    private val vibrationVocabulary = VibrationVocabulary(context)
    private val soundClassifier = SoundClassifier(context)
    
    private val _activeAlert = MutableStateFlow<SoundAlert?>(null)
    val activeAlert: StateFlow<SoundAlert?> = _activeAlert.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    
    private var alertDismissJob: Job? = null
    private var modelLoadJob: Job? = null
    
    init {
        loadModel()
    }
    
    /**
     * Load the YAMNet model asynchronously.
     */
    private fun loadModel() {
        modelLoadJob = viewModelScope.launch {
            val isLoaded = soundClassifier.initialize()
            _isModelLoaded.value = isLoaded
        }
    }
    
    /**
     * Trigger a sound alert manually (for testing).
     */
    fun triggerAlert(soundType: VibrationVocabulary.SoundType) {
        triggerAlert(soundType, manualTrigger = true)
    }
    
    /**
     * Trigger a sound alert from detected sound.
     */
    fun triggerAlert(soundType: VibrationVocabulary.SoundType, manualTrigger: Boolean = false) {
        // Cancel any existing alert dismiss timer
        alertDismissJob?.cancel()
        
        // Trigger vibration
        vibrationVocabulary.vibrateForSound(soundType)
        
        // Create alert based on sound type
        val alert = when (soundType) {
            VibrationVocabulary.SoundType.SIREN -> SoundAlert(
                id = generateAlertId(),
                title = "Siren Detected",
                message = "Emergency vehicle approaching",
                soundType = soundType,
                isEmergency = true,
                timestamp = System.currentTimeMillis()
            )
            VibrationVocabulary.SoundType.FIRE_ALARM -> SoundAlert(
                id = generateAlertId(),
                title = "Fire Alarm",
                message = "Fire alarm sounding nearby",
                soundType = soundType,
                isEmergency = true,
                timestamp = System.currentTimeMillis()
            )
            VibrationVocabulary.SoundType.DOORBELL -> SoundAlert(
                id = generateAlertId(),
                title = "Doorbell",
                message = "Someone at the door",
                soundType = soundType,
                isEmergency = false,
                timestamp = System.currentTimeMillis()
            )
            VibrationVocabulary.SoundType.DOG_BARKING -> SoundAlert(
                id = generateAlertId(),
                title = "Dog Barking",
                message = "Dog barking nearby",
                soundType = soundType,
                isEmergency = false,
                timestamp = System.currentTimeMillis()
            )
            VibrationVocabulary.SoundType.BABY_CRYING -> SoundAlert(
                id = generateAlertId(),
                title = "Baby Crying",
                message = "Baby crying detected",
                soundType = soundType,
                isEmergency = false,
                timestamp = System.currentTimeMillis()
            )
            VibrationVocabulary.SoundType.CAR_HORN -> SoundAlert(
                id = generateAlertId(),
                title = "Car Horn",
                message = "Car horn sounding",
                soundType = soundType,
                isEmergency = false,
                timestamp = System.currentTimeMillis()
            )
        }
        
        _activeAlert.value = alert
        
        // Auto-dismiss after 6 seconds
        alertDismissJob = viewModelScope.launch {
            delay(6000)
            dismissAlert()
        }
    }
    
    /**
     * Dismiss the current alert.
     */
    fun dismissAlert() {
        _activeAlert.value = null
        vibrationVocabulary.cancel()
    }
    
    /**
     * Toggle sound listening.
     */
    fun toggleListening() {
        _isListening.value = !_isListening.value
        
        if (_isListening.value) {
            startListening()
        } else {
            stopListening()
        }
    }
    
    /**
     * Start listening for environmental sounds.
     */
    private fun startListening() {
        // This would integrate with AudioStreamer to continuously process audio
        // For now, this is a placeholder for the actual implementation
    }
    
    /**
     * Stop listening for environmental sounds.
     */
    private fun stopListening() {
        // Stop audio processing
    }
    
    /**
     * Process audio data and detect sounds.
     */
    fun processAudio(audioData: FloatArray) {
        if (!_isListening.value || !_isModelLoaded.value) {
            return
        }
        
        val soundType = soundClassifier.classifyAudio(audioData)
        if (soundType != null) {
            triggerAlert(soundType)
        }
    }
    
    /**
     * Generate a unique alert ID.
     */
    private fun generateAlertId(): String {
        return "alert_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        alertDismissJob?.cancel()
        modelLoadJob?.cancel()
        vibrationVocabulary.cancel()
        soundClassifier.release()
    }
}

/**
 * Represents a sound alert.
 */
data class SoundAlert(
    val id: String,
    val title: String,
    val message: String,
    val soundType: VibrationVocabulary.SoundType,
    val isEmergency: Boolean,
    val timestamp: Long
)
