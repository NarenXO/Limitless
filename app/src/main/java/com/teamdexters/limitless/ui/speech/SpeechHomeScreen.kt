package com.teamdexters.limitless.ui.speech

import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.speech.data.PhraseUsageDatabase
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

/**
 * Represents a single quick-phrase card shown on the Speech Home screen.
 *
 * @param phrase  The phrase that will be read aloud via TTS when selected.
 * @param icon    A flat, single-colour Material icon representing the phrase.
 */
private data class PhraseCard(
    val phrase: String,
    val icon: ImageVector
)

/** Default phrase set for static fallback / cold-start. */
private val defaultPhrases: List<PhraseCard> = listOf(
    PhraseCard("I need help",             Icons.Default.Help),
    PhraseCard("Thank you",               Icons.Default.Handshake),
    PhraseCard("Yes",                     Icons.Default.Done),
    PhraseCard("No",                      Icons.Default.Close),
    PhraseCard("Where is the restroom?",  Icons.Default.AccessibilityNew),
    PhraseCard("I am lost",               Icons.Default.DirectionsWalk),
    PhraseCard("Please speak slowly",     Icons.Default.RecordVoiceOver),
    PhraseCard("Call emergency",          Icons.Default.Call),
    PhraseCard("I need water",            Icons.Default.WaterDrop),
    PhraseCard("I am in pain",            Icons.Default.LocalHospital)
)

// ---------------------------------------------------------------------------
// Screen composable
// ---------------------------------------------------------------------------

/**
 * Speech Home Screen - Phase 2: On-Device Phrase Prediction.
 *
 * Features:
 * - Adaptive prediction grid powered by PhrasePredictionEngine & local Room DB.
 * - Cold-start fallback: displays default 10 phrases until usage data accumulates.
 * - Dynamic "Predicted for you" subheader appears when predictions are active.
 * - Android TextToSpeech speaks the phrase on card tap or switch confirm.
 * - Full switch-scanning support via Modifier.focusable and Modifier.onFocusChanged.
 * - TalkBack semantics on every card.
 * - Haptic feedback on tap.
 *
 * Design tokens used:
 *   - Background  : LimitlessBackground (#F7F1EE)
 *   - Card surface : SurfaceTint (#E0F2F4)
 *   - Accent       : PersonaSpeech (#DDDD7B)
 *   - Body text    : TextPrimary (#1F1F1F)
 */
@Composable
fun SpeechHomeScreen() {
    val context = LocalContext.current
    val haptic  = LocalHapticFeedback.current
    val scope   = rememberCoroutineScope()

    // -- Local Phrase Usage Database & Prediction Engine -----------------------
    val database = remember(context) { PhraseUsageDatabase.getDatabase(context) }
    val engine   = remember(database) { PhrasePredictionEngine(database.phraseUsageDao()) }

    var currentPhrases by remember { mutableStateOf(defaultPhrases) }
    var isPredictedActive by remember { mutableStateOf(false) }

    // -- Load initial predictions or fallback ---------------------------------
    LaunchedEffect(engine) {
        withContext(Dispatchers.IO) {
            val hasData = engine.hasUsageData()
            val defaultTexts = defaultPhrases.map { it.phrase }
            val predictedTexts = engine.getPredictedPhrases(defaultTexts)

            val iconMap = defaultPhrases.associate { it.phrase to it.icon }
            val rankedCards = predictedTexts.map { text ->
                PhraseCard(phrase = text, icon = iconMap[text] ?: Icons.Default.Help)
            }

            withContext(Dispatchers.Main) {
                currentPhrases = rankedCards
                isPredictedActive = hasData
            }
        }
    }

    // -- TextToSpeech lifecycle -----------------------------------------------
    var ttsReady by remember { mutableStateOf(false) }
    val tts      = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engineTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.US
                ttsReady = true
            }
        }
        tts.value = engineTts
        onDispose {
            engineTts.stop()
            engineTts.shutdown()
            tts.value = null
        }
    }

    // -- Speak helper ---------------------------------------------------------
    fun speakPhrase(phrase: String) {
        tts.value?.let { engineTts ->
            if (ttsReady) {
                engineTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase)
            }
        }
    }

    // -- Focused card tracker (for switch-scan highlight) ---------------------
    var focusedIndex by remember { mutableIntStateOf(-1) }

    // -- Layout ---------------------------------------------------------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "Speech Home Screen. Quick Phrases." }
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Header
        Text(
            text = "Quick Phrases",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = PersonaSpeech,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Quick Phrases header" }
        )

        if (isPredictedActive) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Predicted for you",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PersonaSpeech,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Predicted for you subheader" }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phrase grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(currentPhrases) { index, card ->
                val isFocused = focusedIndex == index

                PhraseCardItem(
                    card = card,
                    isFocused = isFocused,
                    onFocusChange = { focused ->
                        if (focused) focusedIndex = index
                        else if (focusedIndex == index) focusedIndex = -1
                    },
                    onSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        speakPhrase(card.phrase)

                        // Record usage and update predictions on IO thread
                        scope.launch(Dispatchers.IO) {
                            engine.recordUsage(card.phrase)
                            val hasData = engine.hasUsageData()
                            val defaultTexts = defaultPhrases.map { it.phrase }
                            val updatedTexts = engine.getPredictedPhrases(defaultTexts)

                            val iconMap = defaultPhrases.associate { it.phrase to it.icon }
                            val updatedCards = updatedTexts.map { text ->
                                PhraseCard(phrase = text, icon = iconMap[text] ?: Icons.Default.Help)
                            }

                            withContext(Dispatchers.Main) {
                                currentPhrases = updatedCards
                                isPredictedActive = hasData
                            }
                        }
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Card composable
// ---------------------------------------------------------------------------

/**
 * A single phrase card.
 *
 * @param card           Data for this card (phrase + icon).
 * @param isFocused      Whether this card currently holds Switch-Access focus.
 * @param onFocusChange  Called with true when focus enters, false when it leaves.
 * @param onSelect       Invoked on tap or switch-confirm.
 */
@Composable
private fun PhraseCardItem(
    card: PhraseCard,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    onSelect: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (isFocused) 1.04f else 1.0f,
        animationSpec = tween(durationMillis = 120),
        label         = "card_scale"
    )

    val borderWidth = if (isFocused) 2.5.dp else 1.dp
    val borderColor = if (isFocused) PersonaSpeech else PersonaSpeech.copy(alpha = 0.30f)

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .background(SurfaceTint, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = card.phrase
                onClick(label = "Speak ${card.phrase}") {
                    onSelect()
                    true
                }
            }
            .clickable(onClickLabel = "Speak ${card.phrase}") {
                onSelect()
            }
            .focusable()
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            }
            .padding(vertical = 20.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector  = card.icon,
                contentDescription = null,
                tint         = TextPrimary,
                modifier     = Modifier.size(36.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text       = card.phrase,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = TextPrimary,
                textAlign  = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
