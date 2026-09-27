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
 * ViewModel for managing sound alert state.
 * Coordinates between SoundClassifier and VibrationController to provide
 * real-time sound detection and alert feedback.
 */
class SoundAlertViewModel : ViewModel() {
    private val _currentAlert = MutableStateFlow<SoundAlert?>(null)
    val currentAlert: StateFlow<SoundAlert?> = _currentAlert.asStateFlow()

    private val _isAlertEnabled = MutableStateFlow(false)
    val isAlertEnabled: StateFlow<Boolean> = _isAlertEnabled.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var soundClassifier: SoundClassifier? = null
    private var vibrationController: VibrationController? = null
    private var alertClearJob: Job? = null

    companion object {
        private const val ALERT_AUTO_CLEAR_MS = 6000L // 6 seconds
    }

    /**
     * Initialize the sound alert system.
     * @param context Android context
     */
    fun initialize(context: Context) {
        try {
            if (soundClassifier == null) {
                soundClassifier = SoundClassifier(context)
            }
            if (vibrationController == null) {
                vibrationController = VibrationController(context)
            }

            viewModelScope.launch {
                try {
                    val loaded = soundClassifier?.loadModel() ?: false
                    _modelLoaded.value = loaded
                    if (!loaded) {
                        val error = soundClassifier?.getLoadError()
                        _errorMessage.value = if (error?.contains("yamnet.tflite") == true) {
                            "YAMNet model missing — using test buttons only"
                        } else {
                            "Model failed to load"
                        }
                    }
                } catch (e: Exception) {
                    _modelLoaded.value = false
                    _errorMessage.value = "YAMNet model missing — using test buttons only"
                }
            }
        } catch (e: Exception) {
            // Never crash on initialization
            _modelLoaded.value = false
            _errorMessage.value = "YAMNet model missing — using test buttons only"
        }
    }

    /**
     * Enable or disable sound alerts.
     * @param enabled Whether alerts should be active
     */
    fun setAlertEnabled(enabled: Boolean) {
        _isAlertEnabled.value = enabled
        if (!enabled) {
            clearAlert()
        }
    }

    /**
     * Process audio data and detect sounds.
     * @param audioData Float array of audio samples
     */
    fun processAudio(audioData: FloatArray) {
        if (!_isAlertEnabled.value) {
            return
        }

        val classifier = soundClassifier
        if (classifier == null || !classifier.isReady()) {
            return
        }

        viewModelScope.launch {
            val detectedSound = classifier.classify(audioData)
            if (detectedSound != null) {
                triggerAlert(detectedSound)
            }
        }
    }

    /**
     * Trigger an alert for the detected sound type.
     * @param soundType Type of sound detected
     */
    private fun triggerAlert(soundType: SoundType) {
        // Cancel any existing clear job
        alertClearJob?.cancel()

        // Create new alert
        val alert = SoundAlert(
            soundType = soundType,
            timestamp = System.currentTimeMillis(),
            message = getMessageForSoundType(soundType)
        )

        _currentAlert.value = alert

        // Trigger vibration
        vibrationController?.vibrate(soundType)

        // Auto-clear after delay
        alertClearJob = viewModelScope.launch {
            delay(ALERT_AUTO_CLEAR_MS)
            clearAlert()
        }
    }

    /**
     * Manually trigger a test alert (for UI testing).
     * @param soundType Type of sound to simulate
     */
    fun triggerTestAlert(soundType: SoundType) {
        triggerAlert(soundType)
    }

    /**
     * Clear the current alert.
     */
    fun clearAlert() {
        alertClearJob?.cancel()
        _currentAlert.value = null
        vibrationController?.cancel()
    }

    /**
     * Get human-readable message for sound type.
     */
    private fun getMessageForSoundType(soundType: SoundType): String {
        return when (soundType) {
            SoundType.SIREN -> "Emergency vehicle siren detected"
            SoundType.FIRE_ALARM -> "Fire alarm or smoke detector detected"
            SoundType.DOORBELL -> "Doorbell or knock detected"
        }
    }

    /**
     * Release resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        alertClearJob?.cancel()
        soundClassifier?.release()
        vibrationController?.cancel()
        soundClassifier = null
        vibrationController = null
    }
}

/**
 * Data class representing a sound alert.
 */
data class SoundAlert(
    val soundType: SoundType,
    val timestamp: Long,
    val message: String
)