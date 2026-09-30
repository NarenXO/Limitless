package com.teamdexters.limitless.speech

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class AACPhraseCard(
    val id: String,
    val category: String,
    val text: String,
    val icon: ImageVector
)

class AACCategoryManager {
    fun getCategories(): List<String> = listOf("Emergency", "Daily Needs", "Navigation", "Social")

    fun getPhraseCards(language: String, category: String): List<AACPhraseCard> {
        return when (language) {
            "ta" -> getTamilCards(category)
            "hi" -> getHindiCards(category)
            else -> getEnglishCards(category)
        }
    }

    private fun getEnglishCards(category: String): List<AACPhraseCard> {
        return when (category) {
            "Emergency" -> listOf(
                AACPhraseCard("e1", category, "I need help", Icons.Default.Warning),
                AACPhraseCard("e2", category, "Call emergency", Icons.Default.Call),
                AACPhraseCard("e3", category, "I am in pain", Icons.Default.Healing),
                AACPhraseCard("e4", category, "I am lost", Icons.Default.Help)
            )
            "Daily Needs" -> listOf(
                AACPhraseCard("d1", category, "I need water", Icons.Default.LocalDrink),
                AACPhraseCard("d2", category, "I need food", Icons.Default.Restaurant),
                AACPhraseCard("d3", category, "Where is the restroom?", Icons.Default.Wc),
                AACPhraseCard("d4", category, "I need to rest", Icons.Default.Bedtime)
            )
            "Navigation" -> listOf(
                AACPhraseCard("n1", category, "Where is the exit?", Icons.Default.MeetingRoom),
                AACPhraseCard("n2", category, "Where is the elevator?", Icons.Default.Elevator),
                AACPhraseCard("n3", category, "Where is the ramp?", Icons.Default.Accessible),
                AACPhraseCard("n4", category, "I want to go home", Icons.Default.Home)
            )
            "Social" -> listOf(
                AACPhraseCard("s1", category, "Thank you", Icons.Default.ThumbUp),
                AACPhraseCard("s2", category, "Yes", Icons.Default.Check),
                AACPhraseCard("s3", category, "No", Icons.Default.Close),
                AACPhraseCard("s4", category, "Please wait", Icons.Default.PanTool)
            )
            else -> emptyList()
        }
    }

    private fun getTamilCards(category: String): List<AACPhraseCard> {
        return when (category) {
            "Emergency" -> listOf(
                AACPhraseCard("e1", category, "எனக்கு உதவி தேவை", Icons.Default.Warning),
                AACPhraseCard("e2", category, "அவசர உதவி அழையுங்கள்", Icons.Default.Call),
                AACPhraseCard("e3", category, "எனக்கு வலி இருக்கிறது", Icons.Default.Healing),
                AACPhraseCard("e4", category, "நான் தொலைந்துவிட்டேன்", Icons.Default.Help)
            )
            "Daily Needs" -> listOf(
                AACPhraseCard("d1", category, "எனக்கு தண்ணீர் தேவை", Icons.Default.LocalDrink),
                AACPhraseCard("d2", category, "எனக்கு உணவு தேவை", Icons.Default.Restaurant),
                AACPhraseCard("d3", category, "கழிவறை எங்கே?", Icons.Default.Wc),
                AACPhraseCard("d4", category, "நான் ஓய்வெடுக்க வேண்டும்", Icons.Default.Bedtime)
            )
            "Navigation" -> listOf(
                AACPhraseCard("n1", category, "வெளியேறும் வழி எங்கே?", Icons.Default.MeetingRoom),
                AACPhraseCard("n2", category, "மின்தூக்கி எங்கே?", Icons.Default.Elevator),
                AACPhraseCard("n3", category, "சாய்வுதளம் எங்கே?", Icons.Default.Accessible),
                AACPhraseCard("n4", category, "நான் வீட்டிற்கு செல்ல வேண்டும்", Icons.Default.Home)
            )
            "Social" -> listOf(
                AACPhraseCard("s1", category, "நன்றி", Icons.Default.ThumbUp),
                AACPhraseCard("s2", category, "ஆம்", Icons.Default.Check),
                AACPhraseCard("s3", category, "இல்லை", Icons.Default.Close),
                AACPhraseCard("s4", category, "காத்திருங்கள்", Icons.Default.PanTool)
            )
            else -> emptyList()
        }
    }

    private fun getHindiCards(category: String): List<AACPhraseCard> {
        return when (category) {
            "Emergency" -> listOf(
                AACPhraseCard("e1", category, "मुझे मदद चाहिए", Icons.Default.Warning),
                AACPhraseCard("e2", category, "आपातकालीन सहायता बुलाएं", Icons.Default.Call),
                AACPhraseCard("e3", category, "मुझे दर्द हो रहा है", Icons.Default.Healing),
                AACPhraseCard("e4", category, "मैं खो गया हूँ", Icons.Default.Help)
            )
            "Daily Needs" -> listOf(
                AACPhraseCard("d1", category, "मुझे पानी चाहिए", Icons.Default.LocalDrink),
                AACPhraseCard("d2", category, "मुझे खाना चाहिए", Icons.Default.Restaurant),
                AACPhraseCard("d3", category, "शौचालय कहाँ है?", Icons.Default.Wc),
                AACPhraseCard("d4", category, "मुझे आराम करना है", Icons.Default.Bedtime)
            )
            "Navigation" -> listOf(
                AACPhraseCard("n1", category, "निकास कहाँ है?", Icons.Default.MeetingRoom),
                AACPhraseCard("n2", category, "लिफ्ट कहाँ है?", Icons.Default.Elevator),
                AACPhraseCard("n3", category, "रैंप कहाँ है?", Icons.Default.Accessible),
                AACPhraseCard("n4", category, "मुझे घर जाना है", Icons.Default.Home)
            )
            "Social" -> listOf(
                AACPhraseCard("s1", category, "धन्यवाद", Icons.Default.ThumbUp),
                AACPhraseCard("s2", category, "हाँ", Icons.Default.Check),
                AACPhraseCard("s3", category, "नहीं", Icons.Default.Close),
                AACPhraseCard("s4", category, "कृपया प्रतीक्षा करें", Icons.Default.PanTool)
            )
            else -> emptyList()
        }
    }
}
