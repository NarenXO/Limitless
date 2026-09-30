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
     * @return The matched HazelIntent (NavigateTo, specific Assist, or GeneralQuery)
     */
    fun routeIntent(spokenText: String): HazelIntent
}

/**
 * Comprehensive on-device implementation of [IntentRouter] using rule-based and
 * synonym keyword matching. Formats input, strips filler words, and deterministically
 * maps spoken English phrases to offline accessibility features or [HazelIntent.GeneralQuery].
 */
class DefaultIntentRouter : IntentRouter {

    override fun routeIntent(spokenText: String): HazelIntent {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) {
            return HazelIntent.GeneralQuery(spokenText)
        }

        val cleaned = normalizeText(trimmed)

        // 1. Scanner Specific Matching
        if (matchesAny(cleaned, SCANNER_KEYWORDS)) {
            val target = extractTarget(cleaned, SCANNER_KEYWORDS)
            return HazelIntent.OpenScanner(target)
        }

        // 2. Community Specific Matching
        if (matchesAny(cleaned, COMMUNITY_KEYWORDS)) {
            val filter = extractTarget(cleaned, COMMUNITY_KEYWORDS)
            return HazelIntent.OpenCommunity(filter)
        }

        // 3. Blind / Vision Features
        when {
            matchesAny(cleaned, READ_TEXT_KEYWORDS) -> return HazelIntent.BlindAssist("READ_TEXT")
            matchesAny(cleaned, DETECT_COLOR_KEYWORDS) -> return HazelIntent.BlindAssist("DETECT_COLOR")
            matchesAny(cleaned, DESCRIBE_SCENE_KEYWORDS) -> return HazelIntent.VisionQuery(spokenText)
            matchesAny(cleaned, SURROUNDINGS_KEYWORDS) -> return HazelIntent.BlindAssist("SURROUNDINGS")
            matchesAny(cleaned, BLIND_GENERAL_KEYWORDS) -> return HazelIntent.NavigateTo(Screen.BlindHome.route, "Blind Assist")
        }

        // 4. Deaf / Hearing Features
        when {
            matchesAny(cleaned, CAPTION_KEYWORDS) -> return HazelIntent.DeafAssist("CAPTIONS")
            matchesAny(cleaned, SOUND_ALERT_KEYWORDS) -> return HazelIntent.DeafAssist("SOUND_ALERTS")
            matchesAny(cleaned, TRANSLATE_KEYWORDS) -> return HazelIntent.DeafAssist("TRANSLATE")
            matchesAny(cleaned, DEAF_GENERAL_KEYWORDS) -> return HazelIntent.NavigateTo(Screen.DeafHome.route, "Deaf Assist")
        }

        // 5. Speech / Communication Features
        when {
            matchesAny(cleaned, EMERGENCY_PHRASE_KEYWORDS) -> return HazelIntent.SpeechAssist("EMERGENCY")
            matchesAny(cleaned, TYPE_TO_SPEECH_KEYWORDS) -> return HazelIntent.SpeechAssist("TYPE_TO_SPEECH")
            matchesAny(cleaned, EMOTION_CARD_KEYWORDS) -> return HazelIntent.SpeechAssist("EMOTION_CARDS")
            matchesAny(cleaned, PHRASE_CARD_GENERAL_KEYWORDS) -> return HazelIntent.OpenPhraseCards()
            matchesAny(cleaned, SPEECH_GENERAL_KEYWORDS) -> return HazelIntent.NavigateTo(Screen.SpeechHome.route, "Speech Assist")
        }

        // 6. Mobility / Accessible Navigation Features
        when {
            matchesAny(cleaned, ACCESSIBLE_ROUTING_KEYWORDS) -> {
                val destination = extractDestination(cleaned)
                return HazelIntent.MobilityAssist("ROUTING")
            }
            matchesAny(cleaned, INDOOR_MOBILITY_KEYWORDS) -> return HazelIntent.MobilityAssist("INDOOR")
            matchesAny(cleaned, MOBILITY_GENERAL_KEYWORDS) -> return HazelIntent.NavigateTo(Screen.MobilityHome.route, "Mobility Assist")
        }

        // 7. Persona / Main Menu Navigation
        if (matchesAny(cleaned, PERSONA_SELECT_KEYWORDS)) {
            return HazelIntent.NavigateTo(Screen.PersonaSelect.route, "Persona Select")
        }

        // 8. Fallback to GeneralQuery
        return HazelIntent.GeneralQuery(rawQuery = spokenText)
    }

    private fun normalizeText(text: String): String {
        var lower = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val fillerPattern = Regex("^\\s*(hey|hazel|please|can you|could you|i want to|i need to|would you|tell me|show me|open|bring me to|take me to|find|start)\\s+")
        var previous = ""
        while (lower != previous) {
            previous = lower
            lower = lower.replace(fillerPattern, "").trim()
        }
        return lower.replace(Regex("\\s+"), " ").trim()
    }

    private fun matchesAny(text: String, keywords: Array<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun extractTarget(text: String, keywords: Array<String>): String? {
        for (kw in keywords) {
            if (text.contains(kw)) {
                val remainder = text.replace(kw, "").trim()
                if (remainder.isNotEmpty()) return remainder
            }
        }
        return null
    }

    private fun extractDestination(text: String): String? {
        val toIdx = text.indexOf(" to ")
        if (toIdx != -1 && toIdx + 4 < text.length) {
            return text.substring(toIdx + 4).trim()
        }
        return null
    }

    companion object {
        private val SCANNER_KEYWORDS = arrayOf(
            "scan building", "accessibility score", "scan ramp", "check accessibility",
            "building score", "audit", "scan entrance", "scan place", "scan location",
            "scan this building"
        )

        private val COMMUNITY_KEYWORDS = arrayOf(
            "community reports", "community feedback", "community", "reviews",
            "report place", "report obstacle", "obstacle report"
        )

        private val READ_TEXT_KEYWORDS = arrayOf(
            "read text", "read document", "read sign", "read this", "ocr", "read"
        )

        private val DETECT_COLOR_KEYWORDS = arrayOf(
            "detect color", "color recognition", "what color", "color scanner", "color"
        )

        private val DESCRIBE_SCENE_KEYWORDS = arrayOf(
            "describe scene", "describe picture", "what is this", "what is in front of me",
            "describe image", "describe", "what am i looking at", "read this", "what's this"
        )

        private val SURROUNDINGS_KEYWORDS = arrayOf(
            "surroundings", "look around", "what is around me", "explore surroundings", "see"
        )

        private val BLIND_GENERAL_KEYWORDS = arrayOf(
            "blind", "vision", "camera assist", "blind assist"
        )

        private val CAPTION_KEYWORDS = arrayOf(
            "live caption", "live captions", "caption", "captions", "transcribe", "subtitles"
        )

        private val SOUND_ALERT_KEYWORDS = arrayOf(
            "sound alert", "sound alerts", "alarm", "doorbell", "listen for sounds", "sound detector"
        )

        private val TRANSLATE_KEYWORDS = arrayOf(
            "translate", "sign translation", "translate speech"
        )

        private val DEAF_GENERAL_KEYWORDS = arrayOf(
            "deaf", "hearing", "deaf assist", "listen"
        )

        private val EMERGENCY_PHRASE_KEYWORDS = arrayOf(
            "emergency phrase", "emergency card", "emergency phrase card", "sos phrase", "emergency"
        )

        private val TYPE_TO_SPEECH_KEYWORDS = arrayOf(
            "type to speech", "speak for me", "talk for me", "type speech", "tts"
        )

        private val EMOTION_CARD_KEYWORDS = arrayOf(
            "emotion card", "emotion cards", "emotions", "feeling cards"
        )

        private val PHRASE_CARD_GENERAL_KEYWORDS = arrayOf(
            "phrase card", "phrase cards", "phrases"
        )

        private val SPEECH_GENERAL_KEYWORDS = arrayOf(
            "speech", "talk", "communication", "speech assist"
        )

        private val ACCESSIBLE_ROUTING_KEYWORDS = arrayOf(
            "accessible route", "wheelchair route", "wheelchair accessible route",
            "accessible path", "find route", "navigate", "directions", "how to reach",
            "accessible navigation", "routing"
        )

        private val INDOOR_MOBILITY_KEYWORDS = arrayOf(
            "elevator", "lift", "ramp", "indoor navigation", "indoor"
        )

        private val MOBILITY_GENERAL_KEYWORDS = arrayOf(
            "mobility", "wheelchair", "mobility assist"
        )

        private val PERSONA_SELECT_KEYWORDS = arrayOf(
            "change mode", "switch persona", "switch assist persona", "select assist",
            "select persona", "change persona", "home", "main menu", "persona select",
            "persona", "change assist mode", "switch mode"
        )
    }
}