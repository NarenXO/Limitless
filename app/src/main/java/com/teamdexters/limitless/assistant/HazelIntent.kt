package com.teamdexters.limitless.assistant

/**
 * Sealed class representing all recognized Hazel assistant intents.
 * Each intent corresponds to a specific action or navigation destination.
 */
sealed class HazelIntent {
    /**
     * Intent to navigate to one of the application routes.
     * @param route The navigation route string (e.g., "blind-home", "deaf-home", "persona-select")
     * @param description Optional human-readable description of the route
     */
    data class NavigateTo(
        val route: String,
        val description: String = ""
    ) : HazelIntent()

    /**
     * Intent to open the accessibility scanner.
     * @param target Optional target or location to scan
     */
    data class OpenScanner(
        val target: String? = null
    ) : HazelIntent()

    /**
     * Intent to open community reports and feedback.
     * @param filter Optional category filter for reports
     */
    data class OpenCommunity(
        val filter: String? = null
    ) : HazelIntent()

    /**
     * Intent to open quick phrase cards for speech assist.
     * @param category Optional phrase category
     */
    data class OpenPhraseCards(
        val category: String? = null
    ) : HazelIntent()

    /**
     * Intent to open accessible routing and navigation.
     * @param destination Target destination name or address
     */
    data class OpenNavigation(
        val destination: String? = null
    ) : HazelIntent()

    /**
     * Intent for blind and low-vision accessibility features.
     * @param subAction Specific action ("READ_TEXT", "DETECT_COLOR", "DESCRIBE_SCENE", "SURROUNDINGS")
     */
    data class BlindAssist(
        val subAction: String
    ) : HazelIntent()

    /**
     * Intent for deaf and hard-of-hearing accessibility features.
     * @param subAction Specific action ("CAPTIONS", "SOUND_ALERTS", "TRANSLATE")
     */
    data class DeafAssist(
        val subAction: String
    ) : HazelIntent()

    /**
     * Intent for speech-impaired accessibility features.
     * @param subAction Specific action ("TYPE_TO_SPEECH", "EMOTION_CARDS", "EMERGENCY")
     */
    data class SpeechAssist(
        val subAction: String
    ) : HazelIntent()

    /**
     * Intent for mobility and wheelchair accessibility features.
     * @param subAction Specific action ("ROUTING", "SCANNER", "COMMUNITY", "INDOOR")
     */
    data class MobilityAssist(
        val subAction: String
    ) : HazelIntent()

    /**
     * Unmatched / freeform query intent handed over to Gemini in Phase 3.
     * @param rawQuery The original spoken text that couldn't be matched locally
     */
    data class GeneralQuery(
        val rawQuery: String
    ) : HazelIntent()

    /**
     * Fallback intent for unrecognized queries (retained for backward compatibility).
     * @param rawQuery The original spoken text that couldn't be matched
     */
    data class Unknown(
        val rawQuery: String
    ) : HazelIntent()
}