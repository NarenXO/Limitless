package com.teamdexters.limitless.blind

import android.util.Log

/**
 * Voice command handler for Blind Assist.
 * Parses spoken text queries into intent enums for AI processing.
 */
object BlindVoiceCommandHandler {

    private const val TAG = "LIMITLESS_TRACE"

    /**
     * Intent enum for blind assist voice commands.
     */
    enum class BlindIntent {
        DESCRIBE_SURROUNDINGS,
        WHATS_IN_FRONT,
        READ_TEXT,
        DETECT_OBSTACLES,
        WHAT_COLOR,
        START_NAVIGATION,
        TELL_ROUTE,
        STOP_NAVIGATION,
        UNKNOWN
    }

    /**
     * Parse spoken text query into intent enum.
     * @param query The spoken text from user
     * @return The detected intent or UNKNOWN if no match
     */
    fun parseQuery(query: String): BlindIntent {
        val normalizedQuery = query.lowercase().trim()

        val intent = when {
            // Start navigation
            normalizedQuery.contains("start navigation") ||
            normalizedQuery.contains("navigate") ||
            normalizedQuery.contains("guide me") ||
            normalizedQuery.contains("take me to") -> BlindIntent.START_NAVIGATION

            // Tell route
            normalizedQuery.contains("tell me the route") ||
            normalizedQuery.contains("current route") ||
            normalizedQuery.contains("next step") ||
            normalizedQuery.contains("where to next") -> BlindIntent.TELL_ROUTE

            // Stop navigation
            normalizedQuery.contains("stop navigation") ||
            normalizedQuery.contains("cancel route") ||
            normalizedQuery.contains("end route") -> BlindIntent.STOP_NAVIGATION

            // Describe surroundings
            normalizedQuery.contains("describe") ||
            normalizedQuery.contains("surroundings") ||
            normalizedQuery.contains("what's around") ||
            normalizedQuery.contains("where am i") -> BlindIntent.DESCRIBE_SURROUNDINGS

            // What's in front
            normalizedQuery.contains("in front") ||
            normalizedQuery.contains("front of me") ||
            normalizedQuery.contains("ahead") -> BlindIntent.WHATS_IN_FRONT

            // Read text
            normalizedQuery.contains("read") ||
            normalizedQuery.contains("read this") ||
            normalizedQuery.contains("read label") ||
            normalizedQuery.contains("read text") ||
            normalizedQuery.contains("what does it say") -> BlindIntent.READ_TEXT

            // Detect obstacles
            normalizedQuery.contains("obstacle") ||
            normalizedQuery.contains("obstacles") ||
            normalizedQuery.contains("any obstacles") ||
            normalizedQuery.contains("what's blocking") -> BlindIntent.DETECT_OBSTACLES

            // What color
            normalizedQuery.contains("color") ||
            normalizedQuery.contains("what color") -> BlindIntent.WHAT_COLOR

            // Unknown intent
            else -> BlindIntent.UNKNOWN
        }

        Log.d(TAG, "BlindVoiceCommandHandler: Parsed query='$query' -> intent=$intent")

        return intent
    }

    /**
     * Get accessibility vision prompt for the given intent.
     * @param intent The detected intent
     * @return Custom prompt for Gemini API
     */
    fun getPromptForIntent(intent: BlindIntent): String {
        return when (intent) {
            BlindIntent.START_NAVIGATION,
            BlindIntent.TELL_ROUTE,
            BlindIntent.STOP_NAVIGATION ->
                ""

            BlindIntent.DESCRIBE_SURROUNDINGS ->
                "Describe the scene in this image in 1-2 concise sentences. Focus on main objects, layout, and spatial relationships."

            BlindIntent.WHATS_IN_FRONT ->
                "What is directly in front of the camera in this image? Answer in 1-2 concise sentences."

            BlindIntent.READ_TEXT ->
                "Read any visible text in this image. If no text is found, say 'No text visible'. Answer in 1-2 sentences."

            BlindIntent.DETECT_OBSTACLES ->
                "Are there any obstacles or hazards in this image? Describe them in 1-2 concise sentences."

            BlindIntent.WHAT_COLOR ->
                "What is the dominant color of the main object in this image? Answer in 1-2 sentences."

            BlindIntent.UNKNOWN ->
                "Describe what you see in this image in 1-2 concise sentences."
        }
    }

    // TODO(Naren): Wire HazelQueryHandler to call BlindVoiceCommandHandler.handleQuery()
}
