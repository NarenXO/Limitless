package com.teamdexters.limitless.ui.persona

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.data.local.entity.PersonaPreferenceEntity
import com.teamdexters.limitless.ui.navigation.Screen
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "PersonaSelectScreen"

/**
 * Two-stage accessibility onboarding screen.
 *
 * Stage 1 — Name Capture:
 *   Greets the user via TTS, then opens SpeechRecognizer to capture their name.
 *   Real-time listening feedback is displayed in a HighlightBox card.
 *
 * Stage 2 — Persona Selection:
 *   Greets the user by name via TTS, then opens SpeechRecognizer to listen
 *   for the desired persona. Tapping a card also works directly.
 *
 * SpeechRecognizer errors (timeout / no match) politely prompt the user to
 * retry via TTS instead of failing silently.
 */
@Composable
fun PersonaSelectScreen(
    navController: NavController,
    database: LimitlessDatabase?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ── Stage tracking ──────────────────────────────────────────────────────
    // true  = Stage 1 (name capture)
    // false = Stage 2 (persona selection)
    var isNameStage by remember { mutableStateOf(true) }
    var capturedName by remember { mutableStateOf("") }

    // ── Speech-recognition UI state ─────────────────────────────────────────
    var isListening by remember { mutableStateOf(false) }
    var listeningHint by remember { mutableStateOf("") }

    // ── TTS ──────────────────────────────────────────────────────────────────
    var ttsRef: TextToSpeech? by remember { mutableStateOf(null) }
    // SpeechRecognizer MUST be created on the UI thread
    var recognizerRef: SpeechRecognizer? by remember { mutableStateOf(null) }

    // ── Helper: build a fresh RecognitionListener ────────────────────────────
    /**
     * Creates a RecognitionListener that handles both the name-capture stage
     * and the persona-selection stage.
     *
     * @param onResult Invoked with the best-match string on success.
     * @param onRetry  Invoked when a recoverable error occurs (timeout / no match).
     */
    fun makeRecognitionListener(
        onResult: (String) -> Unit,
        onRetry: () -> Unit
    ): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            listeningHint = "Listening…"
        }
        override fun onBeginningOfSpeech() {
            listeningHint = "Hearing you…"
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            listeningHint = "Processing…"
        }
        override fun onResults(results: Bundle?) {
            isListening = false
            listeningHint = ""
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()?.trim() ?: ""
            if (best.isNotEmpty()) {
                onResult(best)
            } else {
                onRetry()
            }
        }
        override fun onError(error: Int) {
            isListening = false
            listeningHint = ""
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH       -> "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                SpeechRecognizer.ERROR_AUDIO          -> "ERROR_AUDIO"
                SpeechRecognizer.ERROR_NETWORK        -> "ERROR_NETWORK"
                else                                  -> "ERROR_$error"
            }
            Log.w(TAG, "SpeechRecognizer error: $msg (code=$error)")
            onRetry()
        }
        override fun onPartialResults(partial: Bundle?) {
            val partials = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            partials?.firstOrNull()?.let { listeningHint = "\"$it\"" }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Helper: start recognition ─────────────────────────────────────────────
    fun startListening(listener: RecognitionListener) {
        val recognizer = recognizerRef ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
    }

    // ── Helper: TTS-then-listen (waits for TTS to finish before listening) ────
    fun speakThenListen(utterance: String, onTtsDone: () -> Unit) {
        val tts = ttsRef ?: run { onTtsDone(); return }
        tts.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, null)
        // Poll until TTS is no longer speaking, then fire callback
        coroutineScope.launch {
            while (tts.isSpeaking) delay(150)
            delay(300) // brief pause before mic activates
            withContext(Dispatchers.Main) { onTtsDone() }
        }
    }

    // ── Stage 1 name-retry loop ───────────────────────────────────────────────
    fun startNameCapture() {
        val nameListener = makeRecognitionListener(
            onResult = { name ->
                capturedName = name.replaceFirstChar { it.uppercase() }
                isNameStage = false
            },
            onRetry = {
                speakThenListen("I didn't catch that. What is your name?") {
                    startListening(
                        makeRecognitionListener(
                            onResult = { name ->
                                capturedName = name.replaceFirstChar { it.uppercase() }
                                isNameStage = false
                            },
                            onRetry = {
                                // Two strikes — skip name, proceed with placeholder
                                capturedName = "there"
                                isNameStage = false
                            }
                        )
                    )
                }
            }
        )
        startListening(nameListener)
    }

    // ── Stage 2 persona-selection via voice ────────────────────────────────────
    fun startPersonaListening(saveFn: (String) -> Unit) {
        val personaListener = makeRecognitionListener(
            onResult = { spoken ->
                val lower = spoken.lowercase()
                val match = when {
                    lower.contains("blind") || lower.contains("vision")    -> "BLIND"
                    lower.contains("deaf")  || lower.contains("hear")      -> "DEAF"
                    lower.contains("speech") || lower.contains("speak")    -> "SPEECH"
                    lower.contains("mobility") || lower.contains("wheel")  -> "MOBILITY"
                    else -> null
                }
                if (match != null) {
                    saveFn(match)
                } else {
                    speakThenListen(
                        "Sorry, I didn't recognise that. Please say Blind, Deaf, Speech, or Mobility."
                    ) { startPersonaListening(saveFn) }
                }
            },
            onRetry = {
                speakThenListen(
                    "I didn't catch that. Please say one of: Blind, Deaf, Speech, or Mobility."
                ) { startPersonaListening(saveFn) }
            }
        )
        startListening(personaListener)
    }

    // ── Lifecycle: init TTS + SpeechRecognizer (UI thread) ───────────────────
    DisposableEffect(Unit) {
        // SpeechRecognizer must be on UI thread — DisposableEffect runs on composition
        recognizerRef = SpeechRecognizer.createSpeechRecognizer(context)

        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef?.language = Locale.US
                // Stage 1: welcome + listen for name
                ttsRef?.speak(
                    "Welcome to Limitless. What is your name?",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "welcome"
                )
            }
        }
        ttsRef = tts

        onDispose {
            tts.stop()
            tts.shutdown()
            recognizerRef?.destroy()
            recognizerRef = null
            ttsRef = null
        }
    }

    // ── Stage 1 auto-listen after TTS welcome finishes ────────────────────────
    LaunchedEffect(ttsRef) {
        if (ttsRef == null) return@LaunchedEffect
        // Wait for TTS to initialise and finish the welcome utterance
        while (ttsRef?.isSpeaking != false) delay(200)
        delay(400)
        if (isNameStage) startNameCapture()
    }

    // ── Stage 2 auto-listen after name is captured ────────────────────────────
    LaunchedEffect(isNameStage, capturedName) {
        if (isNameStage) return@LaunchedEffect
        val greeting = "Hello $capturedName. " +
                "Choose your assist mode: " +
                "Blind and Low Vision, Deaf and Hard of Hearing, Speech Impaired, or Mobility and Wheelchair."
        speakThenListen(greeting) {
            startPersonaListening { persona ->
                savePersonaAndNavigate(persona, navController, database, coroutineScope)
            }
        }
    }

    // ── Root layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = isNameStage,
            transitionSpec = {
                (fadeIn() + slideInVertically { it / 2 }) togetherWith
                        (fadeOut() + slideOutVertically { -it / 2 })
            },
            label = "stage_transition"
        ) { nameStage ->
            if (nameStage) {
                NameCaptureStage(
                    isListening = isListening,
                    listeningHint = listeningHint
                )
            } else {
                PersonaSelectionStage(
                    capturedName = capturedName,
                    isListening = isListening,
                    listeningHint = listeningHint,
                    onPersonaSelected = { persona ->
                        savePersonaAndNavigate(persona, navController, database, coroutineScope)
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stage 1 — Name Capture
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stage 1 UI: large HighlightBox card asking "What is your name?"
 * with real-time listening feedback.
 */
@Composable
private fun NameCaptureStage(
    isListening: Boolean,
    listeningHint: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Main prompt card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HighlightBox, RoundedCornerShape(20.dp))
                .padding(32.dp)
                .semantics { contentDescription = "Name capture. What is your name?" },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "What is your name?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = "Speak your name when the mic activates.",
                    fontSize = 14.sp,
                    color = TextPrimary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Mic status indicator
        MicStatusIndicator(isListening = isListening, hint = listeningHint)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stage 2 — Persona Selection
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stage 2 UI: personalised heading + 4 persona cards + mic status.
 * Cards are tappable directly; voice selection also works.
 */
@Composable
private fun PersonaSelectionStage(
    capturedName: String,
    isListening: Boolean,
    listeningHint: String,
    onPersonaSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Greeting heading
        Text(
            text = if (capturedName == "there") "Choose your assist mode"
                   else "Hello, $capturedName!",
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = "Select your assist mode or speak your choice",
            fontSize = 14.sp,
            color = TextPrimary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Persona Cards Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonaCard(
                    title = "Blind & Low-Vision",
                    color = PersonaBlind,
                    icon = Icons.Default.Visibility,
                    onClick = { onPersonaSelected("BLIND") },
                    modifier = Modifier.weight(1f)
                )
                PersonaCard(
                    title = "Deaf & Hard-of-Hearing",
                    color = PersonaDeaf,
                    icon = Icons.Default.Hearing,
                    onClick = { onPersonaSelected("DEAF") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonaCard(
                    title = "Speech-Impaired",
                    color = PersonaSpeech,
                    icon = Icons.Default.QuestionAnswer,
                    onClick = { onPersonaSelected("SPEECH") },
                    modifier = Modifier.weight(1f)
                )
                PersonaCard(
                    title = "Mobility & Wheelchair",
                    color = PersonaMobility,
                    icon = Icons.Default.Accessible,
                    onClick = { onPersonaSelected("MOBILITY") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mic status indicator
        MicStatusIndicator(isListening = isListening, hint = listeningHint)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pulsing mic icon with a hint label — shown while the SpeechRecognizer is active.
 * Disappears when not listening.
 */
@Composable
private fun MicStatusIndicator(isListening: Boolean, hint: String) {
    AnimatedVisibility(
        visible = isListening,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics {
                contentDescription = if (hint.isNotEmpty()) hint else "Microphone active"
            }
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(LimitlessPrimary, CircleShape)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }
            if (hint.isNotEmpty()) {
                Text(
                    text = hint,
                    fontSize = 13.sp,
                    color = TextPrimary.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Composable card for persona selection.
 * Full accessibility semantics + high contrast design tokens.
 */
@Composable
fun PersonaCard(
    title: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .background(color, RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = TextPrimary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "$title. Tap to select this assist mode."
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = LimitlessTypography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DB + Navigation helper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Saves the selected persona to Room database and navigates to the appropriate home screen.
 */
private fun savePersonaAndNavigate(
    persona: String,
    navController: NavController,
    database: LimitlessDatabase?,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            try {
                database?.personaPreferenceDao()?.setPreference(
                    PersonaPreferenceEntity(
                        id = 1,
                        selectedPersona = persona,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                // If database save fails, continue with navigation
            }
        }
        val destination = when (persona) {
            "BLIND"    -> Screen.BlindHome.route
            "DEAF"     -> Screen.DeafHome.route
            "SPEECH"   -> Screen.SpeechHome.route
            "MOBILITY" -> Screen.MobilityHome.route
            else       -> Screen.PersonaSelect.route
        }
        navController.navigate(destination) {
            popUpTo(Screen.PersonaSelect.route) { inclusive = true }
        }
    }
}