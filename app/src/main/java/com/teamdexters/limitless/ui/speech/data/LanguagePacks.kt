package com.teamdexters.limitless.ui.speech.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

/**
 * A single phrase card entry within a language pack.
 * Carries its display text, the spoken phrase (may differ for emphasis),
 * and an optional icon for the UI grid.
 */
data class PackPhrase(
    val displayText: String,
    val spokenPhrase: String,
    val icon: ImageVector
)

/**
 * A single emotion card entry within a language pack.
 *
 * @param emotionKey   Stable English key used for icon mapping ("happy", "sad", …).
 * @param label        Short display label shown on the card in the selected language.
 * @param spokenPhrase Full phrase spoken by TTS when the card is tapped.
 */
data class EmotionPhrase(
    val emotionKey: String,
    val label: String,
    val spokenPhrase: String
)

/**
 * Localised section header strings for the Speech Home Screen.
 */
data class SectionHeaders(
    val quickPhrases: String,
    val predictedForYou: String,
    val howAreYouFeeling: String,
    val typeToSpeak: String,
    val speak: String,
    val emergency: String
)

/**
 * Complete language pack for a single locale.
 */
data class LanguagePack(
    val code: String,
    val displayName: String,
    val ttsLocale: Locale,
    val quickPhrases: List<PackPhrase>,
    val emotionPhrases: List<EmotionPhrase>,
    val emergencyMessage: String,
    val typeToSpeakHint: String,
    val sectionHeaders: SectionHeaders
)

// ---------------------------------------------------------------------------
// Icon map shared across all packs (icons are language-neutral)
// ---------------------------------------------------------------------------

private val quickPhraseIcons: List<ImageVector> = listOf(
    Icons.Default.Help,
    Icons.Default.Handshake,
    Icons.Default.Done,
    Icons.Default.Close,
    Icons.Default.AccessibilityNew,
    Icons.Default.DirectionsWalk,
    Icons.Default.RecordVoiceOver,
    Icons.Default.Call,
    Icons.Default.WaterDrop,
    Icons.Default.LocalHospital
)

private val emotionIcons: List<ImageVector> = listOf(
    Icons.Default.SentimentSatisfied,      // happy
    Icons.Default.SentimentDissatisfied,   // sad
    Icons.Default.Warning,                 // scared
    Icons.Default.SentimentVeryDissatisfied, // angry
    Icons.Default.Restaurant,              // hungry
    Icons.Default.LocalDrink,             // thirsty
    Icons.Default.Bedtime,                // tired
    Icons.Default.Healing                 // pain
)

// ---------------------------------------------------------------------------
// English pack (canonical — never change phrases or order)
// ---------------------------------------------------------------------------

private val englishPhraseTexts = listOf(
    "I need help",
    "Thank you",
    "Yes",
    "No",
    "Where is the restroom?",
    "I am lost",
    "Please speak slowly",
    "Call emergency",
    "I need water",
    "I am in pain"
)

private val englishPack = LanguagePack(
    code = "en",
    displayName = "English",
    ttsLocale = Locale.US,
    quickPhrases = englishPhraseTexts.mapIndexed { i, text ->
        PackPhrase(displayText = text, spokenPhrase = text, icon = quickPhraseIcons[i])
    },
    emotionPhrases = listOf(
        EmotionPhrase("happy",   "Happy",   "I am happy"),
        EmotionPhrase("sad",     "Sad",     "I am sad"),
        EmotionPhrase("scared",  "Scared",  "I am scared"),
        EmotionPhrase("angry",   "Angry",   "I am angry"),
        EmotionPhrase("hungry",  "Hungry",  "I am hungry"),
        EmotionPhrase("thirsty", "Thirsty", "I am thirsty"),
        EmotionPhrase("tired",   "Tired",   "I am tired"),
        EmotionPhrase("pain",    "Pain",    "I am in pain")
    ),
    emergencyMessage = "Emergency. I need help immediately. Please call for assistance.",
    typeToSpeakHint = "Type anything to speak...",
    sectionHeaders = SectionHeaders(
        quickPhrases    = "Quick Phrases",
        predictedForYou = "Predicted for you",
        howAreYouFeeling = "How are you feeling?",
        typeToSpeak     = "Type to Speech",
        speak           = "Speak",
        emergency       = "EMERGENCY"
    )
)

// ---------------------------------------------------------------------------
// Tamil (தமிழ்) pack
// ---------------------------------------------------------------------------

private val tamilPhraseTexts = listOf(
    "எனக்கு உதவி தேவை",
    "நன்றி",
    "ஆம்",
    "இல்லை",
    "கழிவறை எங்கே?",
    "நான் தொலைந்துவிட்டேன்",
    "மெதுவாகப் பேசுங்கள்",
    "அவசர உதவி அழையுங்கள்",
    "எனக்கு தண்ணீர் தேவை",
    "எனக்கு வலி இருக்கிறது"
)

private val tamilPack = LanguagePack(
    code = "ta",
    displayName = "தமிழ்",
    ttsLocale = Locale("ta", "IN"),
    quickPhrases = tamilPhraseTexts.mapIndexed { i, text ->
        PackPhrase(displayText = text, spokenPhrase = text, icon = quickPhraseIcons[i])
    },
    emotionPhrases = listOf(
        EmotionPhrase("happy",   "மகிழ்ச்சி",  "நான் மகிழ்ச்சியாக இருக்கிறேன்"),
        EmotionPhrase("sad",     "சோகம்",      "நான் சோகமாக இருக்கிறேன்"),
        EmotionPhrase("scared",  "பயம்",       "நான் பயப்படுகிறேன்"),
        EmotionPhrase("angry",   "கோபம்",      "நான் கோபமாக இருக்கிறேன்"),
        EmotionPhrase("hungry",  "பசி",        "எனக்கு பசிக்கிறது"),
        EmotionPhrase("thirsty", "தாகம்",      "எனக்கு தாகமாக இருக்கிறது"),
        EmotionPhrase("tired",   "சோர்வு",     "நான் சோர்வாக இருக்கிறேன்"),
        EmotionPhrase("pain",    "வலி",        "எனக்கு வலி இருக்கிறது")
    ),
    emergencyMessage = "அவசரம். எனக்கு உடனடி உதவி தேவை. தயவுசெய்து உதவிக்கு அழையுங்கள்.",
    typeToSpeakHint = "பேச எதையாவது தட்டச்சு செய்யுங்கள்...",
    sectionHeaders = SectionHeaders(
        quickPhrases     = "விரைவு வாக்கியங்கள்",
        predictedForYou  = "உங்களுக்காக கணிக்கப்பட்டது",
        howAreYouFeeling = "நீங்கள் எப்படி உணர்கிறீர்கள்?",
        typeToSpeak      = "பேச தட்டச்சு செய்யுங்கள்",
        speak            = "பேசு",
        emergency        = "அவசரம்"
    )
)

// ---------------------------------------------------------------------------
// Hindi (हिन्दी) pack
// ---------------------------------------------------------------------------

private val hindiPhraseTexts = listOf(
    "मुझे मदद चाहिए",
    "धन्यवाद",
    "हाँ",
    "नहीं",
    "शौचालय कहाँ है?",
    "मैं खो गया हूँ",
    "कृपया धीरे बोलें",
    "आपातकालीन सहायता बुलाएं",
    "मुझे पानी चाहिए",
    "मुझे दर्द हो रहा है"
)

private val hindiPack = LanguagePack(
    code = "hi",
    displayName = "हिन्दी",
    ttsLocale = Locale("hi", "IN"),
    quickPhrases = hindiPhraseTexts.mapIndexed { i, text ->
        PackPhrase(displayText = text, spokenPhrase = text, icon = quickPhraseIcons[i])
    },
    emotionPhrases = listOf(
        EmotionPhrase("happy",   "खुश",      "मैं खुश हूँ"),
        EmotionPhrase("sad",     "उदास",     "मैं उदास हूँ"),
        EmotionPhrase("scared",  "डरा हुआ",  "मैं डरा हुआ हूँ"),
        EmotionPhrase("angry",   "गुस्सा",   "मैं गुस्से में हूँ"),
        EmotionPhrase("hungry",  "भूखा",     "मुझे भूख लगी है"),
        EmotionPhrase("thirsty", "प्यासा",   "मुझे प्यास लगी है"),
        EmotionPhrase("tired",   "थका हुआ",  "मैं थका हुआ हूँ"),
        EmotionPhrase("pain",    "दर्द",     "मुझे दर्द हो रहा है")
    ),
    emergencyMessage = "आपातकाल। मुझे तुरंत मदद चाहिए। कृपया सहायता के लिए बुलाएं।",
    typeToSpeakHint = "बोलने के लिए कुछ टाइप करें...",
    sectionHeaders = SectionHeaders(
        quickPhrases     = "त्वरित वाक्यांश",
        predictedForYou  = "आपके लिए अनुमानित",
        howAreYouFeeling = "आप कैसा महसूस कर रहे हैं?",
        typeToSpeak      = "बोलने के लिए टाइप करें",
        speak            = "बोलें",
        emergency        = "आपातकाल"
    )
)

// ---------------------------------------------------------------------------
// Public accessor
// ---------------------------------------------------------------------------

/**
 * Registry of all supported language packs.
 * Use [getPack] to retrieve the pack for a given language code.
 * Defaults to English if the code is unrecognised.
 */
object LanguagePacks {

    private val allPacks: Map<String, LanguagePack> = mapOf(
        "en" to englishPack,
        "ta" to tamilPack,
        "hi" to hindiPack
    )

    /** Returns the [LanguagePack] for [code] ("en", "ta", "hi"). Defaults to English. */
    fun getPack(code: String): LanguagePack = allPacks[code] ?: englishPack

    /** Ordered list of (code, displayName) for building the language toggle chips. */
    val availableLanguages: List<Pair<String, String>> = listOf(
        "en" to "English",
        "ta" to "தமிழ்",
        "hi" to "हिन्दी"
    )
}
