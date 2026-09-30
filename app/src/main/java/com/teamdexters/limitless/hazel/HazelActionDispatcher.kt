package com.teamdexters.limitless.hazel

import android.util.Log
import com.teamdexters.limitless.core.hazel.HazelCommand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazelActionDispatcher @Inject constructor() {

    private val _systemCommand = MutableSharedFlow<HazelCommand>(extraBufferCapacity = 10)
    val systemCommand = _systemCommand.asSharedFlow()

    fun parseIntent(rawIntent: String): Boolean {
        // Strip punctuation and extra spaces
        val cleanIntent = rawIntent.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

        fun logTrace(intentName: String, actionName: String) {
            Log.d("LIMITLESS_TRACE", "[Hazel] Intent: $intentName -> Action: $actionName")
        }

        return when {
            // Camera / Vision
            Regex("\\b(open camera|camera|take photo|describe|in front|front of me|what is around|what am i holding)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("OpenCameraIntent", "VisionAnalyze")
                _systemCommand.tryEmit(HazelCommand.VisionAnalyze)
                true
            }

            // OCR / Text Reading
            Regex("\\b(scan text|read text|read label|read sign|what does it say|read this)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("ReadTextIntent", "VisionAnalyze") // Maps to vision analyze as well for now
                _systemCommand.tryEmit(HazelCommand.VisionAnalyze)
                true
            }

            // Mobility / Navigation
            Regex("\\b(navigation|navigate|maps|open maps|route|take me to|move from here to)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("NavigationIntent", "Navigate")
                val destination = cleanIntent.replace(Regex("\\b(navigate to|take me to|move from here to)\\b"), "").trim()
                _systemCommand.tryEmit(HazelCommand.Navigate(destination))
                true
            }
            
            // Room Accessibility
            Regex("\\b(is accessible|has ramp|have a ramp|check room)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("RoomAccessibilityIntent", "Navigate")
                _systemCommand.tryEmit(HazelCommand.Navigate("accessibility check"))
                true
            }

            // Flashlight
            Regex("\\b(flashlight on|flashlight off|torch on|torch off)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("FlashlightIntent", "ToggleFlashlight")
                // Missing command for flashlight, just returning true for now
                true
            }

            // Emergency / SOS
            Regex("\\b(emergency|sos|call emergency contact|help me|i fell)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("EmergencyIntent", "StartSOS")
                _systemCommand.tryEmit(HazelCommand.StartSOS)
                true
            }

            // System Status / Settings
            Regex("\\b(battery status|wifi status|bluetooth|settings|open settings)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("SystemSettingsIntent", "OpenSettings")
                true
            }

            // Speech AAC
            Regex("\\b(say|speak)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("SpeechAACIntent", "SpeakAAC")
                val text = cleanIntent.replace(Regex("\\b(say|speak)\\b"), "").trim()
                _systemCommand.tryEmit(HazelCommand.SpeakAAC(text))
                true
            }

            else -> {
                Log.d("LIMITLESS_TRACE", "MasterOrchestrator: Unknown intent $rawIntent. Delegating to CloudLLM.")
                false
            }
        }
    }
}
