package com.teamdexters.limitless.assistant

/**
 * Sealed class representing all recognized Hazel assistant intents.
 * Each intent corresponds to a specific action or navigation destination.
 */
sealed class HazelIntent {
    /**
     * Intent to navigate to one of the 5 core application routes.
     * @param route The navigation route string (e.g., "blind-home", "deaf-home")
     */
    data class NavigateTo(val route: String) : HazelIntent()

    /**
     * Intent for blind and low-vision accessibility features.
     * Placeholder for OCR, object detection, color recognition features.
     * @param action Specific action within the blind module (e.g., "ocr", "object_detection", "color")
     */
    data class BlindAssist(val action: String) : HazelIntent()

    /**
     * Intent for deaf and hard-of-hearing accessibility features.
     * Placeholder for captions, sound alerts, translation features.
     * @param action Specific action within the deaf module (e.g., "captions", "sound_alert", "translation")
     */
    data class DeafAssist(val action: String) : HazelIntent()

    /**
     * Intent for speech-impaired accessibility features.
     * Placeholder for phrase cards, text-to-speech features.
     * @param action Specific action within the speech module (e.g., "phrase_card", "tts")
     */
    data class SpeechAssist(val action: String) : HazelIntent()

    /**
     * Intent for mobility and wheelchair accessibility features.
     * Placeholder for routing, scanning, reporting features.
     * @param action Specific action within the mobility module (e.g., "routing", "scanner", "report")
     */
    data class MobilityAssist(val action: String) : HazelIntent()

    /**
     * Fallback intent for unrecognized queries.
     * Can be optionally boosted with Gemini cloud AI for better understanding.
     * @param rawQuery The original spoken text that couldn't be matched
     */
    data class Unknown(val rawQuery: String) : HazelIntent()
}