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

    private val readTextKeywords = listOf(
        "read text", "read this", "read the", "read label", "read board",
        "read sign", "what does it say", "what's written", "what is written",
        "read opposite", "read in front", "read what", "scan text",
        "tell me what it says", "what does this say", "what does that say"
    )

    private val colorKeywords = listOf(
        "what color", "what colour", "detect color", "detect colour",
        "color of", "colour of", "is this red", "is this blue", "is this green",
        "is this yellow", "is this orange", "is this purple", "is this pink",
        "is this black", "is this white", "identify color", "identify colour",
        "tell me the color", "tell me the colour"
    )

    private val objectKeywords = listOf(
        "detect objects", "what objects", "find objects", "what's ahead",
        "what is ahead", "anything in front", "what is in front",
        "is there a chair", "is there a person", "is there a door",
        "is there a bottle", "is there a table", "any objects",
        "what can you see", "what do you see", "what's there",
        "objects around", "things around", "spot objects"
    )

    private val describeKeywords = listOf(
        "describe surroundings", "describe my surroundings", "describe the scene",
        "what's around me", "what is around me", "what's around",
        "what's nearby", "tell me the scene", "scene description",
        "where am i", "where are we", "describe where", "surroundings",
        "tell me about surroundings"
    )

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
     * Priority: READ_TEXT > COLOR > OBJECTS > DESCRIBE > NAV > STOP > REPEAT > HELP > UNKNOWN
     *
     * @param spokenText Raw text from SpeechRecognizer
     * @return The matched VoiceIntent
     */
    fun route(spokenText: String): VoiceIntent {
        val q = spokenText.lowercase().trim()

        val intent = when {
            // Highest priority: explicit text reading
            readTextKeywords.any { q.contains(it) }    -> VoiceIntent.READ_TEXT

            // Color detection
            colorKeywords.any { q.contains(it) }       -> VoiceIntent.COLOR_DETECTION

            // Object detection (before describe to catch "what's ahead")
            objectKeywords.any { q.contains(it) }      -> VoiceIntent.OBJECT_DETECTION

            // Scene description
            describeKeywords.any { q.contains(it) }    -> VoiceIntent.DESCRIBE_SURROUNDINGS

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
