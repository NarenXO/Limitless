package com.teamdexters.limitless.roommapping

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class LiveNavigationEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating

    private var activeSteps: List<SpatialRouteStep> = emptyList()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
    }

    fun startNavigation(steps: List<SpatialRouteStep>) {
        if (steps.isEmpty()) return
        activeSteps = steps
        _currentStepIndex.value = 0
        _isNavigating.value = true
        announceCurrentStep()
    }

    fun nextStep() {
        if (_currentStepIndex.value < activeSteps.size - 1) {
            _currentStepIndex.value += 1
            announceCurrentStep()
        } else {
            announceDestinationReached()
        }
    }

    fun previousStep() {
        if (_currentStepIndex.value > 0) {
            _currentStepIndex.value -= 1
            announceCurrentStep()
        }
    }

    fun stopNavigation() {
        _isNavigating.value = false
        tts?.speak("Navigation stopped.", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun announceCurrentStep() {
        val step = activeSteps.getOrNull(_currentStepIndex.value) ?: return
        val text = "Step ${_currentStepIndex.value + 1} of ${activeSteps.size}. ${step.instructionText}. Landmark: ${step.landmarkReference}."
        Log.d("LIMITLESS_TRACE", "LiveNav: $text")

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)

        // Directional Haptic Cues
        when (step.hapticCueType) {
            "TURN_LEFT" -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 150, 100), -1))
            "TURN_RIGHT" -> vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 100, 100, 100), -1))
            "RAMP_UP" -> vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
            else -> vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun announceDestinationReached() {
        _isNavigating.value = false
        val text = "You have reached your destination!"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150, 100, 300), -1))
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
