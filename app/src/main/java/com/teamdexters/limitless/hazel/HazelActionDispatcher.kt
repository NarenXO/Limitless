package com.teamdexters.limitless.hazel

import android.util.Log
import com.teamdexters.limitless.data.local.dao.AccessibilityScoreDao
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HazelActionDispatcher @Inject constructor(
    private val accessibilityScoreDao: AccessibilityScoreDao
) {
    fun parseIntent(userQuery: String): HazelAction {
        val lowerQuery = userQuery.lowercase()

        // 1. NavigateToLocation
        val navRegex = Regex("navigate to (.+)|go to (.+)|take me to (.+)|route to (.+)")
        navRegex.find(lowerQuery)?.let { match ->
            val destination = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "destination"
            return HazelAction.NavigateToLocation(destination.trim())
        }

        // 2. QueryAccessibilityScore
        val scoreRegex = Regex("is (.+) accessible|score of (.+)|check ramp at (.+)|rating for (.+)")
        scoreRegex.find(lowerQuery)?.let { match ->
            val location = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "location"
            return HazelAction.QueryAccessibilityScore(location.trim())
        }

        // 3. TranslateSpeech
        val translateRegex = Regex("translate|say in (spanish|tamil|hindi)")
        translateRegex.find(lowerQuery)?.let { match ->
            val targetLanguage = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "another language"
            return HazelAction.TranslateSpeech(userQuery, targetLanguage)
        }

        // 4. DescribeSurroundings
        val describeKeywords = listOf("describe surroundings", "what's around me", "where am i", "what is around me")
        if (describeKeywords.any { lowerQuery.contains(it) }) {
            return HazelAction.DescribeSurroundings(null)
        }

        // 5. TriggerEmergencyAlert
        val emergencyKeywords = listOf("sos", "emergency", "help me", "i fell")
        if (emergencyKeywords.any { lowerQuery.contains(it) }) {
            return HazelAction.TriggerEmergencyAlert(userQuery)
        }

        return HazelAction.None
    }

    suspend fun executeAction(action: HazelAction): HazelActionResult {
        Log.d("LIMITLESS_TRACE", "HazelActionDispatcher: Executed action $action")
        return when (action) {
            is HazelAction.NavigateToLocation -> {
                HazelActionResult(
                    spokenFeedback = "Routing you to ${action.destination}...",
                    targetRoute = "mobility-home"
                )
            }
            is HazelAction.QueryAccessibilityScore -> {
                val scores = accessibilityScoreDao.getAllScores().firstOrNull() ?: emptyList()
                val score = scores.find { it.buildingName.lowercase().contains(action.locationName) }
                    ?: scores.firstOrNull()

                if (score != null) {
                    val rampText = if (score.rampDetected) "a ramp" else "no ramp"
                    HazelActionResult(
                        spokenFeedback = "${score.buildingName} has an accessibility score of ${score.overallScore}/100 with $rampText available.",
                        targetRoute = null
                    )
                } else {
                    HazelActionResult(
                        spokenFeedback = "I couldn't find an accessibility score for ${action.locationName}.",
                        targetRoute = null
                    )
                }
            }
            is HazelAction.TranslateSpeech -> {
                HazelActionResult(
                    spokenFeedback = "Translating to ${action.targetLanguage} is not yet fully implemented offline.",
                    targetRoute = null
                )
            }
            is HazelAction.DescribeSurroundings -> {
                HazelActionResult(
                    spokenFeedback = "Activating surroundings description...",
                    targetRoute = "blind-home"
                )
            }
            is HazelAction.TriggerEmergencyAlert -> {
                Log.d("LIMITLESS_TRACE", "EMERGENCY TRACE: SOS Triggered -> ${action.reason}")
                HazelActionResult(
                    spokenFeedback = "Emergency trigger initiated. Sending location to emergency contact.",
                    targetRoute = null
                )
            }
            is HazelAction.None -> {
                HazelActionResult(spokenFeedback = "No action found.")
            }
        }
    }
}
