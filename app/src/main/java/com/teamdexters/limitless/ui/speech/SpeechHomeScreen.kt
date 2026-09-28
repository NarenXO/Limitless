package com.teamdexters.limitless.ui.speech

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.speech.data.PhraseUsageDatabase
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ---------------------------------------------------------------------------
// Data models
// ---------------------------------------------------------------------------

/**
 * Represents a single quick-phrase card shown on the Speech Home screen.
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

/** Emotion communication card model for Section B. */
private data class EmotionCard(
    val emotion: String,
    val phrase: String,
    val icon: ImageVector
)

private val emotionCards: List<EmotionCard> = listOf(
    EmotionCard("Happy",   "I am happy",    Icons.Default.SentimentSatisfied),
    EmotionCard("Sad",     "I am sad",      Icons.Default.SentimentDissatisfied),
    EmotionCard("Scared",  "I am scared",   Icons.Default.Warning),
    EmotionCard("Angry",   "I am angry",    Icons.Default.SentimentVeryDissatisfied),
    EmotionCard("Hungry",  "I am hungry",   Icons.Default.Restaurant),
    EmotionCard("Thirsty", "I am thirsty",  Icons.Default.LocalDrink),
    EmotionCard("Tired",   "I am tired",    Icons.Default.Bedtime),
    EmotionCard("Pain",    "I am in pain",  Icons.Default.Healing)
)

// ---------------------------------------------------------------------------
// Screen composable
// ---------------------------------------------------------------------------

/**
 * Speech Home Screen - Phase 3: TTS, Emotion Cards, and Emergency Button.
 *
 * @param onBack Callback triggered when tapping the top back button.
 */
@Composable
fun SpeechHomeScreen(
    onBack: () -> Unit = {}
) {
    val context     = LocalContext.current
    val haptic      = LocalHapticFeedback.current
    val scope       = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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

    // -- TextToSpeech lifecycle (SINGLE shared instance for entire screen) -----
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
            if (ttsReady && phrase.isNotBlank()) {
                engineTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase)
            }
        }
    }

    // -- Focused card tracker for switch-scanning -----------------------------
    var focusedIndex by remember { mutableIntStateOf(-1) }

    // -- Section A state -------------------------------------------------------
    var typedText by remember { mutableStateOf("") }
    var isSpeakButtonFocused by remember { mutableStateOf(false) }

    // -- Section C state & handler (REAL HARDWARE VIBRATOR FIX) ----------------
    var isEmergencyFocused by remember { mutableStateOf(false) }

    fun triggerEmergency() {
        speakPhrase("Emergency. I need help immediately. Please call for assistance.")

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 300, 150, 300, 150, 300)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 300, 150, 300, 150, 300), -1)
            }
        } catch (_: Exception) {
            scope.launch {
                repeat(3) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(200)
                }
            }
        }
    }

    // -- Main Layout ----------------------------------------------------------
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        android.util.Log.d("LimitlessNav", "Back button clicked in SpeechHomeScreen")
                        onBack()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Go back to persona selection",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Speech & Communication",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        containerColor = LimitlessBackground
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "Speech Home Screen. Quick Phrases and Communication Tools." }
    ) {

        // 1. Header & Prediction Subheader -------------------------------------
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

        // 2. Phrase Card Grid (Phases 1-2) -------------------------------------
        val phraseRows = currentPhrases.chunked(2)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            phraseRows.forEachIndexed { rowIndex, rowCards ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowCards.forEachIndexed { colIndex, card ->
                        val cardIndex = rowIndex * 2 + colIndex
                        val isFocused = focusedIndex == cardIndex

                        Box(modifier = Modifier.weight(1f)) {
                            PhraseCardItem(
                                card = card,
                                isFocused = isFocused,
                                onFocusChange = { focused ->
                                    if (focused) focusedIndex = cardIndex
                                    else if (focusedIndex == cardIndex) focusedIndex = -1
                                },
                                onSelect = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    speakPhrase(card.phrase)

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
                    if (rowCards.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 3. SECTION A: Type-to-Speech -----------------------------------------
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Type to Speech",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PersonaSpeech,
                modifier = Modifier.semantics { contentDescription = "Type to speech section header" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = typedText,
                    onValueChange = { typedText = it },
                    placeholder = {
                        Text(
                            text = "Type anything to speak...",
                            fontSize = 18.sp,
                            color = TextPrimary.copy(alpha = 0.6f)
                        )
                    },
                    textStyle = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HighlightBox,
                        unfocusedContainerColor = HighlightBox,
                        disabledContainerColor = HighlightBox,
                        focusedIndicatorColor = PersonaSpeech,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .semantics { contentDescription = "Type a message to speak aloud" }
                )

                val speakScale by animateFloatAsState(
                    targetValue = if (isSpeakButtonFocused) 1.04f else 1.0f,
                    animationSpec = tween(120),
                    label = "speak_scale"
                )

                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .scale(speakScale)
                        .background(PersonaSpeech, RoundedCornerShape(16.dp))
                        .border(
                            width = if (isSpeakButtonFocused) 2.5.dp else 1.dp,
                            color = if (isSpeakButtonFocused) TextPrimary else PersonaSpeech.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = "Speak typed message"
                            onClick(label = "Speak typed message") {
                                if (typedText.isNotBlank()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    speakPhrase(typedText)
                                }
                                true
                            }
                        }
                        .clickable {
                            if (typedText.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                speakPhrase(typedText)
                            }
                        }
                        .focusable()
                        .onFocusChanged { isSpeakButtonFocused = it.isFocused }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Speak",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 4. SECTION B: Emotion Cards ------------------------------------------
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "How are you feeling?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PersonaSpeech,
                modifier = Modifier.semantics { contentDescription = "How are you feeling header" }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(emotionCards) { _, emotionCard ->
                    var isEmotionFocused by remember { mutableStateOf(false) }
                    val emotionScale by animateFloatAsState(
                        targetValue = if (isEmotionFocused) 1.04f else 1.0f,
                        animationSpec = tween(120),
                        label = "emotion_scale"
                    )

                    Box(
                        modifier = Modifier
                            .scale(emotionScale)
                            .width(104.dp)
                            .height(104.dp)
                            .background(SurfaceTint, RoundedCornerShape(16.dp))
                            .border(
                                width = if (isEmotionFocused) 2.5.dp else 1.dp,
                                color = if (isEmotionFocused) PersonaSpeech else PersonaSpeech.copy(alpha = 0.30f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .semantics(mergeDescendants = true) {
                                role = Role.Button
                                contentDescription = emotionCard.phrase
                                onClick(label = "Speak ${emotionCard.phrase}") {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    speakPhrase(emotionCard.phrase)
                                    true
                                }
                            }
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                speakPhrase(emotionCard.phrase)
                            }
                            .focusable()
                            .onFocusChanged { isEmotionFocused = it.isFocused }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = emotionCard.icon,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = emotionCard.emotion,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 5. SECTION C: Emergency Button ---------------------------------------
        val emergencyScale by animateFloatAsState(
            targetValue = if (isEmergencyFocused) 1.04f else 1.0f,
            animationSpec = tween(120),
            label = "emergency_scale"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .scale(emergencyScale)
                .background(PersonaSpeech, RoundedCornerShape(16.dp))
                .border(
                    width = if (isEmergencyFocused) 3.dp else 1.dp,
                    color = if (isEmergencyFocused) TextPrimary else PersonaSpeech.copy(alpha = 0.40f),
                    shape = RoundedCornerShape(16.dp)
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Emergency button. Tap to call for help."
                    onClick(label = "Speak emergency message") {
                        triggerEmergency()
                        true
                    }
                }
                .clickable {
                    triggerEmergency()
                }
                .focusable()
                .onFocusChanged { isEmergencyFocused = it.isFocused }
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "EMERGENCY",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    } // end Scaffold
}

// ---------------------------------------------------------------------------
// Card composable
// ---------------------------------------------------------------------------

/**
 * A single phrase card.
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
