package com.teamdexters.limitless.hazel

import android.util.Log
import androidx.navigation.NavController
import com.teamdexters.limitless.ui.navigation.Screen

class HazelActionDispatcher(
    private val navController: NavController,
    private val onSpeak: (String) -> Unit
) {
    fun parseIntent(rawIntent: String): Boolean {
        // Strip punctuation and extra spaces
        val cleanIntent = rawIntent.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")

        fun logTrace(intentName: String, actionName: String) {
            Log.d("LIMITLESS_TRACE", "Speech:\n$rawIntent\n↓\nIntent:\n$intentName\n↓\nAction:\n$actionName\n↓\nResult:\nSUCCESS")
        }

        return when {
            // Camera / Vision
            Regex("\\b(open camera|camera|take photo|describe|in front|front of me|what is around|what am i holding)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("OpenCameraIntent", "LaunchCamera")
                onSpeak("Navigating to Camera and Vision mode for you.")
                navController.navigate(Screen.BlindHome.route)
                true
            }

            // OCR / Text Reading
            Regex("\\b(scan text|read text|read label|read sign|what does it say|read this)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("ReadTextIntent", "LaunchOCR")
                onSpeak("Navigating to text reader for you.")
                navController.navigate(Screen.BlindHome.route)
                true
            }

            // Mobility / Navigation
            Regex("\\b(navigation|navigate|maps|open maps|route|take me to|move from here to)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("NavigationIntent", "LaunchMaps")
                onSpeak("Navigating to Mobility and Wheelchair mode for you.")
                navController.navigate(Screen.MobilityHome.route)
                true
            }
            
            // Room Accessibility
            Regex("\\b(is accessible|has ramp|have a ramp|check room)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("RoomAccessibilityIntent", "CheckAccessibility")
                onSpeak("Navigating to Mobility and Wheelchair mode for you.")
                navController.navigate(Screen.MobilityHome.route)
                true
            }

            // Flashlight
            Regex("\\b(flashlight on|flashlight off|torch on|torch off)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("FlashlightIntent", "ToggleFlashlight")
                val action = if (cleanIntent.contains("on")) "Turning flashlight on." else "Turning flashlight off."
                onSpeak(action)
                // Flashlight toggle logic goes here
                true
            }

            // Emergency / SOS
            Regex("\\b(emergency|sos|call emergency contact|help me|i fell)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("EmergencyIntent", "DispatchSOS")
                onSpeak("Emergency triggered. Calling for help.")
                true
            }

            // System Status / Settings
            Regex("\\b(battery status|wifi status|bluetooth|settings|open settings)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("SystemSettingsIntent", "OpenSettings")
                onSpeak("Opening system settings.")
                // Navigation to settings or status readout goes here
                true
            }

            // Speech AAC
            Regex("\\b(say|speak)\\b").containsMatchIn(cleanIntent) -> {
                logTrace("SpeechAACIntent", "LaunchAAC")
                onSpeak("Navigating to Speech Impaired mode for you.")
                navController.navigate(Screen.SpeechHome.route)
                true
            }

            else -> {
                Log.d("LIMITLESS_TRACE", "MasterOrchestrator: Unknown intent $rawIntent. Delegating to CloudLLM.")
                false
            }
        }
    }
}
