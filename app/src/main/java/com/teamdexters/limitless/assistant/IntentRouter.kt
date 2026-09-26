package com.teamdexters.limitless.assistant

import com.teamdexters.limitless.ui.navigation.Screen

/**
 * Interface for routing spoken text to Hazel intents.
 * Allows for different routing implementations (keyword-based, AI-based, etc.)
 */
interface IntentRouter {
    /**
     * Analyzes spoken text and returns the corresponding Hazel intent.
     * @param spokenText The user's spoken input
     * @return The matched HazelIntent (NavigateTo, specific Assist, or Unknown)
     */
    fun routeIntent(spokenText: String): HazelIntent
}

/**
 * Default implementation of IntentRouter using keyword matching.
 * Provides basic routing logic that can be extended by teammates for their specific modules.
 */
class DefaultIntentRouter : IntentRouter {
    
    override fun routeIntent(spokenText: String): HazelIntent {
        val normalizedText = spokenText.lowercase()
        
        // TODO: Teammates should extend this logic with more sophisticated matching
        // for their specific modules (Blind, Deaf, Speech, Mobility)
        
        return when {
            // Blind-related keywords
            normalizedText.contains("blind") ||
            normalizedText.contains("look") ||
            normalizedText.contains("see") ||
            normalizedText.contains("read text") ||
            normalizedText.contains("what is this") -> {
                HazelIntent.NavigateTo(Screen.BlindHome.route)
            }
            
            // Deaf-related keywords
            normalizedText.contains("deaf") ||
            normalizedText.contains("listen") ||
            normalizedText.contains("caption") ||
            normalizedText.contains("sound alert") ||
            normalizedText.contains("translate") -> {
                HazelIntent.NavigateTo(Screen.DeafHome.route)
            }
            
            // Speech-related keywords
            normalizedText.contains("speech") ||
            normalizedText.contains("talk for me") ||
            normalizedText.contains("phrase") ||
            normalizedText.contains("card") -> {
                HazelIntent.NavigateTo(Screen.SpeechHome.route)
            }
            
            // Mobility-related keywords
            normalizedText.contains("wheelchair") ||
            normalizedText.contains("ramp") ||
            normalizedText.contains("accessible route") ||
            normalizedText.contains("scan building") ||
            normalizedText.contains("report") -> {
                HazelIntent.NavigateTo(Screen.MobilityHome.route)
            }
            
            // Fallback for unrecognized queries
            else -> HazelIntent.Unknown(spokenText)
        }
    }
}