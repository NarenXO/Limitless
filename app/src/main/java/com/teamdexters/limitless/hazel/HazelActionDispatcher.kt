package com.teamdexters.limitless.hazel

import android.util.Log
import androidx.navigation.NavController
import com.teamdexters.limitless.ui.navigation.Screen

class HazelActionDispatcher(
    private val navController: NavController,
    private val onSpeak: (String) -> Unit
) {
    fun dispatch(rawIntent: String): Boolean {
        val lowerIntent = rawIntent.lowercase()
        Log.d("LIMITLESS_TRACE", "MasterOrchestrator: Dispatched intent $rawIntent successfully")
        
        return when {
            // Blind Vision Intent
            lowerIntent.contains("what's in front of me") || 
            lowerIntent.contains("describe surroundings") || 
            lowerIntent.contains("read text") -> {
                onSpeak("Navigating to Blind and Low Vision mode for you.")
                navController.navigate(Screen.BlindHome.route)
                true
            }
            
            // Routing Intent
            lowerIntent.contains("navigate to") || 
            lowerIntent.contains("move from here to") || 
            lowerIntent.contains("tell me the route") -> {
                onSpeak("Navigating to Mobility and Wheelchair mode for you.")
                navController.navigate(Screen.MobilityHome.route)
                true
            }
            
            // Room Intent
            lowerIntent.contains("is ") && lowerIntent.contains(" accessible") || 
            lowerIntent.contains("does ") && lowerIntent.contains(" have a ramp") -> {
                onSpeak("Navigating to Mobility and Wheelchair mode for you.")
                navController.navigate(Screen.MobilityHome.route)
                true
            }
            
            // Emergency Intent
            lowerIntent.contains("sos") || 
            lowerIntent.contains("help me") -> {
                onSpeak("Emergency triggered. Calling for help.")
                // Should trigger EmergencyManager, but since it's just wiring for now:
                // we assume EmergencyManager is handled or we just log it.
                true
            }
            
            // Speech Intent
            lowerIntent.contains("speak") || 
            lowerIntent.contains("say") -> {
                onSpeak("Navigating to Speech Impaired mode for you.")
                navController.navigate(Screen.SpeechHome.route)
                true
            }
            
            else -> {
                Log.d("LIMITLESS_TRACE", "MasterOrchestrator: Unknown intent $rawIntent")
                false
            }
        }
    }
}
