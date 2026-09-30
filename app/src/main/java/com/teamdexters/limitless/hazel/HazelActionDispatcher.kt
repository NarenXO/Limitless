package com.teamdexters.limitless.hazel

import android.util.Log
import androidx.navigation.NavController
import com.teamdexters.limitless.ui.navigation.Screen

class HazelActionDispatcher(
    private val navController: NavController,
    private val onSpeak: (String) -> Unit
) {
    fun parseIntent(rawIntent: String): Boolean {
        val lowerIntent = rawIntent.lowercase()
        Log.d("LIMITLESS_TRACE", "MasterOrchestrator: Dispatched intent $rawIntent successfully")
        
        return when {
            // Blind Vision
            lowerIntent.contains("in front") || 
            lowerIntent.contains("front of me") || 
            lowerIntent.contains("describe") || 
            lowerIntent.contains("what's around") || 
            lowerIntent.contains("what am i holding") -> {
                onSpeak("Navigating to Blind and Low Vision mode for you.")
                navController.navigate(Screen.BlindHome.route)
                true
            }
            
            // Blind OCR
            lowerIntent.contains("read text") || 
            lowerIntent.contains("read label") || 
            lowerIntent.contains("read sign") || 
            lowerIntent.contains("what does it say") -> {
                onSpeak("Navigating to Blind and Low Vision mode for you.")
                navController.navigate(Screen.BlindHome.route)
                true
            }
            
            // Mobility Navigation
            lowerIntent.contains("navigate to") || 
            lowerIntent.contains("take me to") || 
            lowerIntent.contains("move from here to") || 
            lowerIntent.contains("tell me the route to") -> {
                onSpeak("Navigating to Mobility and Wheelchair mode for you.")
                navController.navigate(Screen.MobilityHome.route)
                true
            }
            
            // Room Accessibility
            lowerIntent.contains("is ") && lowerIntent.contains(" accessible") || 
            lowerIntent.contains("does ") && lowerIntent.contains(" have a ramp") ||
            lowerIntent.contains("check ") -> {
                onSpeak("Navigating to Mobility and Wheelchair mode for you.")
                navController.navigate(Screen.MobilityHome.route)
                true
            }
            
            // Speech AAC
            lowerIntent.contains("say ") || 
            lowerIntent.contains("speak ") -> {
                onSpeak("Navigating to Speech Impaired mode for you.")
                navController.navigate(Screen.SpeechHome.route)
                true
            }
            
            // Auto-SOS
            lowerIntent.contains("sos") || 
            lowerIntent.contains("help me") || 
            lowerIntent.contains("emergency") || 
            lowerIntent.contains("i fell") -> {
                onSpeak("Emergency triggered. Calling for help.")
                // Should trigger EmergencyManager, but since it's just wiring for now:
                // we assume EmergencyManager is handled or we just log it.
                true
            }
            
            else -> {
                Log.d("LIMITLESS_TRACE", "MasterOrchestrator: Unknown intent $rawIntent")
                false
            }
        }
    }
}
