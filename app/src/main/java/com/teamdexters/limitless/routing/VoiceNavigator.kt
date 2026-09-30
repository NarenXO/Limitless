package com.teamdexters.limitless.routing

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class NavigationProgress(
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 0,
    val currentStep: RouteStep? = null,
    val isNavigating: Boolean = false,
    val isCompleted: Boolean = false,
    val remainingDistanceMeters: Float = 0f
)

class VoiceNavigator(context: Context) {
    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val pendingUtterances = mutableListOf<String>()

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val _navState = MutableStateFlow(NavigationProgress())
    val navState: StateFlow<NavigationProgress> = _navState.asStateFlow()

    private var activeRoute: Route? = null

    init {
        textToSpeech = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
                isTtsReady = true
                pendingUtterances.forEach { utterance ->
                    textToSpeech?.speak(
                        utterance, 
                        TextToSpeech.QUEUE_ADD, 
                        null, 
                        "nav_queued_${System.currentTimeMillis()}"
                    )
                }
                pendingUtterances.clear()
            } else {
                Log.e("LIMITLESS_TRACE", "VoiceNavigator: TTS Initialization failed")
            }
        }
    }

    fun startNavigation(route: Route) {
        if (route.steps.isEmpty()) return

        activeRoute = route
        val initialDistance = route.totalDistanceMeters
        val firstStep = route.steps.first()

        _navState.value = NavigationProgress(
            currentStepIndex = 0,
            totalSteps = route.steps.size,
            currentStep = firstStep,
            isNavigating = true,
            isCompleted = false,
            remainingDistanceMeters = initialDistance
        )

        val announcement = "Starting navigation. Total distance ${initialDistance.toInt()} meters. First step: ${firstStep.spokenInstruction}"
        speak(announcement)
        triggerDirectionHaptic(firstStep.direction)

        Log.d("LIMITLESS_TRACE", "VoiceNavigator: Started navigation with ${route.steps.size} steps")
    }

    fun nextStep() {
        val route = activeRoute ?: return
        var currentIndex = _navState.value.currentStepIndex
        currentIndex++

        if (currentIndex >= route.steps.size - 1) {
            _navState.value = _navState.value.copy(
                currentStepIndex = route.steps.size - 1,
                currentStep = route.steps.lastOrNull(),
                isNavigating = false,
                isCompleted = true,
                remainingDistanceMeters = 0f
            )
            val instruction = "You have arrived at your destination!"
            speak(instruction)
            triggerDirectionHaptic(StepDirection.DESTINATION)
            Log.d("LIMITLESS_TRACE", "VoiceNavigator: Step ${currentIndex + 1}/${route.steps.size}")
        } else {
            val step = route.steps[currentIndex]
            val remDist = route.steps.drop(currentIndex).sumOf { it.distanceMeters.toDouble() }.toFloat()
            _navState.value = _navState.value.copy(
                currentStepIndex = currentIndex,
                currentStep = step,
                remainingDistanceMeters = remDist
            )
            speak(step.spokenInstruction)
            triggerDirectionHaptic(step.direction)
            Log.d("LIMITLESS_TRACE", "VoiceNavigator: Step ${currentIndex + 1}/${route.steps.size}")
        }
    }

    fun previousStep() {
        val route = activeRoute ?: return
        var currentIndex = _navState.value.currentStepIndex
        currentIndex--
        if (currentIndex < 0) currentIndex = 0

        val step = route.steps[currentIndex]
        val remDist = route.steps.drop(currentIndex).sumOf { it.distanceMeters.toDouble() }.toFloat()
        _navState.value = _navState.value.copy(
            currentStepIndex = currentIndex,
            currentStep = step,
            isNavigating = true,
            isCompleted = false,
            remainingDistanceMeters = remDist
        )
        speak(step.spokenInstruction)
        triggerDirectionHaptic(step.direction)
        Log.d("LIMITLESS_TRACE", "VoiceNavigator: Step ${currentIndex + 1}/${route.steps.size}")
    }

    fun repeatCurrentInstruction() {
        val step = _navState.value.currentStep ?: return
        speak(step.spokenInstruction)
        triggerDirectionHaptic(step.direction)
    }

    fun stopNavigation() {
        textToSpeech?.stop()
        vibrator.cancel()
        _navState.value = NavigationProgress()
        activeRoute = null
        Log.d("LIMITLESS_TRACE", "VoiceNavigator: Navigation stopped")
    }

    fun shutdown() {
        stopNavigation()
        textToSpeech?.shutdown()
        textToSpeech = null
        isTtsReady = false
    }

    private fun speak(text: String) {
        if (isTtsReady && textToSpeech != null) {
            textToSpeech?.speak(
                text, 
                TextToSpeech.QUEUE_FLUSH, 
                null, 
                "nav_${System.currentTimeMillis()}"
            )
        } else {
            pendingUtterances.add(text)
        }
    }

    private fun triggerDirectionHaptic(direction: StepDirection) {
        if (!vibrator.hasVibrator()) return

        val timings: LongArray
        val amplitudes: IntArray

        when (direction) {
            StepDirection.STRAIGHT -> {
                timings = longArrayOf(0, 300)
                amplitudes = intArrayOf(0, 255)
            }
            StepDirection.LEFT -> {
                timings = longArrayOf(0, 100, 150, 100)
                amplitudes = intArrayOf(0, 255, 0, 255)
            }
            StepDirection.RIGHT -> {
                timings = longArrayOf(0, 100, 100, 100, 100, 100)
                amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            }
            StepDirection.RAMP_UP, StepDirection.RAMP_DOWN -> {
                timings = longArrayOf(0, 200)
                amplitudes = intArrayOf(0, 255)
            }
            StepDirection.STAIRS_UP, StepDirection.STAIRS_DOWN -> {
                timings = longArrayOf(0, 400, 100, 400)
                amplitudes = intArrayOf(0, 255, 0, 255)
            }
            StepDirection.DESTINATION -> {
                timings = longArrayOf(0, 500, 150, 120, 100, 120)
                amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasAmplitudeControl()) {
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator.vibrate(effect)
            } else {
                val effect = VibrationEffect.createWaveform(timings, -1)
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    }
}
