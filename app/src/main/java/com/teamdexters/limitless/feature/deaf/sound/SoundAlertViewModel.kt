package com.teamdexters.limitless.feature.deaf.sound

import android.content.Context
import android.util.Log
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
class SoundAlertViewModel : ViewModel() {
    
    private var context: Context? = null
    private var vibrationVocabulary: VibrationVocabulary? = null
    private var soundClassifier: SoundClassifier? = null
    private var audioStreamer: AudioStreamer? = null
    
    private val _activeAlert = MutableStateFlow<SoundAlert?>(null)
    val activeAlert: StateFlow<SoundAlert?> = _activeAlert.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()
    
    private var alertDismissJob: Job? = null
    private var modelLoadJob: Job? = null
    private var audioStreamingJob: Job? = null
    private var enginesInitialized = false
    
    /**
     * Set the context for the ViewModel.
     * Call this from Compose with LocalContext.current.
     */
    fun setContext(ctx: Context) {
        this.context = ctx
        
        // Initialize engines only once when context is available
        if (!enginesInitialized) {
            enginesInitialized = true
            
            // Initialize VibrationVocabulary
            vibrationVocabulary = try {
                VibrationVocabulary(ctx)
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "SoundAlertViewModel: Failed to create VibrationVocabulary", e)
                null
            }
            
            // Initialize SoundClassifier
            soundClassifier = try {
                SoundClassifier(ctx)
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "SoundAlertViewModel: Failed to create SoundClassifier", e)
                null
            }
            
            // Initialize AudioStreamer
            audioStreamer = try {
                AudioStreamer()
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "SoundAlertViewModel: Failed to create AudioStreamer", e)
                null
            }
            
            // Load model after engines are initialized
            loadModel()
        }
    }
    
    /**
     * Load the YAMNet model asynchronously.
     */
    private fun loadModel() {
        modelLoadJob = viewModelScope.launch {
            // Defer loading until context is set
            // If context is null, soundClassifier will also be null
            val classifier = soundClassifier
            val isLoaded = if (context != null && classifier != null) {
                classifier.initialize()
            } else {
                false
            }
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
        vibrationVocabulary?.vibrateForSound(soundType)
        
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
        vibrationVocabulary?.cancel()
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
        if (audioStreamer == null || !audioStreamer!!.initialize()) {
            Log.e("LIMITLESS_TRACE", "SoundAlertViewModel: Failed to initialize AudioStreamer")
            _isListening.value = false
            return
        }
        
        audioStreamingJob = viewModelScope.launch {
            try {
                audioStreamer!!.startStreaming().collect { audioData ->
                    // Convert ByteArray to FloatArray for YAMNet
                    val floatArray = convertToFloatArray(audioData)
                    processAudio(floatArray)
                }
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "SoundAlertViewModel: Audio streaming error", e)
                _isListening.value = false
            }
        }
        
        Log.d("LIMITLESS_TRACE", "SoundAlertViewModel: Started live environmental listening")
    }
    
    /**
     * Stop listening for environmental sounds.
     */
    private fun stopListening() {
        audioStreamingJob?.cancel()
        audioStreamingJob = null
        audioStreamer?.stopStreaming()
        audioStreamer?.release()
        Log.d("LIMITLESS_TRACE", "SoundAlertViewModel: Stopped live environmental listening")
    }
    
    /**
     * Process audio data and detect sounds.
     */
    fun processAudio(audioData: FloatArray) {
        if (!_isListening.value || !_isModelLoaded.value) {
            return
        }
        
        val soundType = soundClassifier?.classifyAudio(audioData)
        if (soundType != null) {
            Log.d("LIMITLESS_TRACE", "SoundAlertViewModel: Detected sound type: $soundType")
            triggerAlert(soundType, manualTrigger = false)
        }
    }
    
    /**
     * Convert ByteArray (16-bit PCM) to FloatArray normalized to [-1.0, 1.0].
     */
    private fun convertToFloatArray(byteArray: ByteArray): FloatArray {
        val floatArray = FloatArray(byteArray.size / 2)
        for (i in floatArray.indices) {
            val sample = ((byteArray[i * 2 + 1].toInt() shl 8) or (byteArray[i * 2].toInt() and 0xFF)).toShort()
            floatArray[i] = sample / 32768.0f
        }
        return floatArray
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
        audioStreamingJob?.cancel()
        vibrationVocabulary?.cancel()
        soundClassifier?.release()
        audioStreamer?.release()
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
