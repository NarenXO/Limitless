package com.teamdexters.limitless.hazel

sealed class HazelAction {
    data class NavigateToLocation(val destination: String) : HazelAction()
    data class QueryAccessibilityScore(val locationName: String) : HazelAction()
    data class TranslateSpeech(val text: String, val targetLanguage: String) : HazelAction()
    data class DescribeSurroundings(val focusArea: String?) : HazelAction()
    data class TriggerEmergencyAlert(val reason: String) : HazelAction()
    object None : HazelAction()
}

data class HazelActionResult(
    val spokenFeedback: String,
    val targetRoute: String? = null
)
