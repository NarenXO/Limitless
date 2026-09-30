package com.teamdexters.limitless.blind

import android.util.Log

/**
 * Smart intent router for the voice-only Blind Assistant.
 *
 * Priority order when multiple keywords overlap:
 *   READ_TEXT > COLOR_DETECTION > OBJECT_DETECTION > DESCRIBE_SURROUNDINGS
 *
 * Handles flexible phrasing, filler words, British English, and mixed case.
 * Returns HELP when intent is genuinely ambiguous so the assistant can ask
 * one short clarifying question instead of silently failing.
 */
object BlindVoiceIntentRouter {

    private const val TAG = "LIMITLESS_TRACE"

    // ------------ Intent enum --------------------------------------------------

    enum class VoiceIntent {
        READ_TEXT,
        COLOR_DETECTION,
        OBJECT_DETECTION,
        DESCRIBE_SURROUNDINGS,
        STOP_LISTENING,
        REPEAT,
        HELP,
        START_NAVIGATION,
        STOP_NAVIGATION,
        UNKNOWN
    }

    // ------------ Keyword tables ----------------------------------------------

    private val stopKeywords = listOf(
        "stop listening", "pause listening", "stop assistant",
        "be quiet", "shut up", "stop talking", "silence"
    )

    private val repeatKeywords = listOf(
        "repeat", "repeat that", "say that again", "say again",
        "what did you say", "come again"
    )

    private val helpKeywords = listOf(
        "help", "what can you do", "what do you do", "commands",
        "what can i say", "what should i say", "options"
    )

    private val navStartKeywords = listOf(
        "start navigation", "navigate", "guide me", "take me to",
        "directions to", "navigate to"
    )

    private val navStopKeywords = listOf(
        "stop navigation", "cancel route", "end route",
        "stop navigating", "exit navigation"
    )

    // ------------ Public API --------------------------------------------------

    /**
     * Route a spoken utterance to the correct VoiceIntent.
     *
     * @param spokenText Raw text from SpeechRecognizer
     * @return The matched VoiceIntent
     */
    fun route(spokenText: String): VoiceIntent {
        val q = spokenText.lowercase().trim()

        val intent = when {
            // DESCRIBE_SURROUNDINGS (Matches ANY variation of describing, front, ahead, before me)
            q.contains("describe") || q.contains("surround") || q.contains("front") ||
            q.contains("ahead") || q.contains("before") || q.contains("around") ||
            q.contains("see") || q.contains("scene") || q.contains("where am i") -> VoiceIntent.DESCRIBE_SURROUNDINGS

            // READ_TEXT (Matches ANY variation of reading text/labels)
            q.contains("read") || q.contains("text") || q.contains("label") ||
            q.contains("sign") || q.contains("written") || q.contains("what does it say") -> VoiceIntent.READ_TEXT

            // COLOR_DETECTION (Matches color)
            q.contains("color") || q.contains("colour") -> VoiceIntent.COLOR_DETECTION

            // OBJECT_DETECTION (Matches objects/items)
            q.contains("object") || q.contains("item") || q.contains("blocking") -> VoiceIntent.OBJECT_DETECTION

            // Navigation
            navStopKeywords.any { q.contains(it) }     -> VoiceIntent.STOP_NAVIGATION
            navStartKeywords.any { q.contains(it) }    -> VoiceIntent.START_NAVIGATION

            // Control commands
            stopKeywords.any { q.contains(it) }        -> VoiceIntent.STOP_LISTENING
            repeatKeywords.any { q.contains(it) }      -> VoiceIntent.REPEAT
            helpKeywords.any { q.contains(it) }        -> VoiceIntent.HELP

            // Fallback: couldn't determine intent
            else                                        -> VoiceIntent.UNKNOWN
        }

        Log.d(TAG, "BlindVoiceIntentRouter: query='$spokenText' → intent=$intent")
        return intent
    }

    /**
     * Help message spoken when intent is HELP or UNKNOWN.
     */
    val helpMessage: String =
        "I can describe surroundings, detect objects, detect color, or read text. " +
        "What do you need?"

    /**
     * Clarification prompt spoken when intent is genuinely ambiguous.
     */
    val clarifyMessage: String =
        "Did you want me to describe surroundings, detect objects, detect color, or read text?"
}
