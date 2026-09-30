package com.teamdexters.limitless.ui.persona

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.teamdexters.limitless.ui.theme.DarkCharcoal
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.NeutralGray
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.PureBlack
import com.teamdexters.limitless.ui.theme.PureWhite
import com.teamdexters.limitless.ui.theme.SubtleDivider
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
    database: LimitlessDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember { 
        context.getSharedPreferences("limitless_user_session", Context.MODE_PRIVATE) 
    }

    // Get current app version code
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) { null }
    }
    val currentVersion = packageInfo?.let { 
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            it.versionCode.toLong()
        }
    } ?: 1L
    val savedVersion = prefs.getLong("onboarding_version", 0L)

    // If app version changed (reinstall/update), reset onboarding
    val isFreshInstall = currentVersion != savedVersion

    // Read from disk: has the user completed the name stage?
    val hasCompletedNameOnDisk = remember { 
        prefs.getBoolean("has_completed_name_stage", false) && !isFreshInstall
    }
    val savedNameOnDisk = remember { 
        if (isFreshInstall) "" else prefs.getString("saved_user_name", "") ?: "" 
    }

    // ── Stage tracking ──────────────────────────────────────────────────────
    // true  = Stage 1 (name capture)
    // false = Stage 2 (persona selection)
    var isNameStage by rememberSaveable { mutableStateOf(!hasCompletedNameOnDisk) }
    var capturedName by rememberSaveable { mutableStateOf(savedNameOnDisk) }
    var nameStrikes by remember { mutableStateOf(0) }

    fun completeNameStage(name: String) {
        val finalName = name.ifBlank { "there" }
        capturedName = finalName
        isNameStage = false
        
        // WRITE SYNCHRONOUSLY TO DISK
        prefs.edit()
            .putBoolean("has_completed_name_stage", true)
            .putString("saved_user_name", finalName)
            .putLong("onboarding_version", currentVersion)
            .apply()
    }

    // ── Speech-recognition UI state ─────────────────────────────────────────
    var isListening by remember { mutableStateOf(false) }
    var listeningHint by remember { mutableStateOf("") }

    // ── TTS & Speech State ───────────────────────────────────────────────────
    var ttsFinished by remember { mutableStateOf(false) }
    var ttsRef: TextToSpeech? by remember { mutableStateOf(null) }
    var recognizerRef: SpeechRecognizer? by remember { mutableStateOf(null) }

    // ── Helper: build a fresh RecognitionListener ────────────────────────────
    fun makeRecognitionListener(
        onResult: (String) -> Unit,
        onRetry: (Int) -> Unit
    ): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onReadyForSpeech at ${System.currentTimeMillis()}")
            isListening = true
            listeningHint = if (isNameStage) "Listening…" else "Listening... Say Blind, Deaf, Speech, or Mobility"
        }
        override fun onBeginningOfSpeech() {
            Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onBeginningOfSpeech")
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
            Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onResults (Captured: \"$best\")")
            if (best.isNotEmpty()) {
                onResult(best)
            } else {
                onRetry(SpeechRecognizer.ERROR_NO_MATCH)
            }
        }
        override fun onError(error: Int) {
            isListening = false
            listeningHint = if (isNameStage) "Tap mic to speak your name" else "Tap card below or tap mic to speak mode"
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH       -> "ERROR_NO_MATCH"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
                SpeechRecognizer.ERROR_AUDIO          -> "ERROR_AUDIO"
                SpeechRecognizer.ERROR_NETWORK        -> "ERROR_NETWORK"
                11                                    -> "ERROR_SERVER_DISCONNECTED (11)"
                else                                  -> "ERROR_$error"
            }
            Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onError ($msg)")
            Log.w(TAG, "SpeechRecognizer error: $msg (code=$error)")
            if (!isNameStage) {
                Log.e("LIMITLESS_TRACE", "[Stage 2] SpeechRecognizer onError: Code $error")
                Log.d("LIMITLESS_TRACE", "[PersonaSelectScreen] Destroy SpeechRecognizer at ${System.currentTimeMillis()}")
                val oldRecognizer = recognizerRef
                recognizerRef = null
                oldRecognizer?.destroy()
            }
            onRetry(error)
        }
        override fun onPartialResults(partial: Bundle?) {
            val partials = partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            partials?.firstOrNull()?.let { listeningHint = "\"$it\"" }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ── Helper: start recognition ─────────────────────────────────────────────
    fun startListening(listener: RecognitionListener) {
            Log.d("LIMITLESS_TRACE", "isRecognitionAvailable: ${SpeechRecognizer.isRecognitionAvailable(context)}")
            val isMainThread = android.os.Looper.getMainLooper().thread == Thread.currentThread()
            Log.d("LIMITLESS_TRACE", "Main thread state: $isMainThread")
        val recognizer = recognizerRef ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        }
        Log.d("LIMITLESS_TRACE", "Recognizer Intent: $intent, EXTRAs: ${intent.extras}")
        recognizer.setRecognitionListener(listener)
        recognizer.startListening(intent)
    }

    // ── Helper: TTS-then-listen (waits for TTS to finish before listening) ────
    val currentOnTtsDone = remember { java.util.concurrent.atomic.AtomicReference<(() -> Unit)?>(null) }

    fun speakThenListen(utterance: String, onTtsDone: () -> Unit) {
        val tts = ttsRef ?: run { onTtsDone(); return }
        currentOnTtsDone.set(onTtsDone)
        tts.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, java.util.UUID.randomUUID().toString())
    }

    // ── Helper: speak prompt ────────────────────────────────────────────────
    fun speakPrompt(utterance: String) {
        ttsFinished = false
        isListening = false
        listeningHint = "Speaking…"
        ttsRef?.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "prompt")

    }

    // ── Stage 1 name-retry loop ───────────────────────────────────────────────
    fun startNameCapture() {
        Log.d("LIMITLESS_TRACE", "[Stage 1] Name Capture started")
        val nameListener = makeRecognitionListener(
            onResult = { name ->
                val enteredName = name.replaceFirstChar { it.uppercase() }
                completeNameStage(enteredName)
            },
            onRetry = { error ->
                nameStrikes++
                if (nameStrikes >= 2) {
                    completeNameStage("there")
                } else {
                    speakPrompt("I didn't catch that. What is your name?")
                }
            }
        )
        startListening(nameListener)
    }

    // ── Stage 2 persona-selection via voice ────────────────────────────────────
    fun startPersonaListening() {
        if (recognizerRef != null) {
            Log.d("LIMITLESS_TRACE", "[PersonaSelectScreen] Destroy previous recognizer before recreating at ${System.currentTimeMillis()}")
            recognizerRef?.destroy()
        }
        recognizerRef = SpeechRecognizer.createSpeechRecognizer(context)
        Log.d("LIMITLESS_TRACE", "SpeechRecognizer creation timestamp: ${System.currentTimeMillis()}")
        val personaListener = makeRecognitionListener(
            onResult = { spoken ->
                val lower = spoken.lowercase()
                val match = when {
                    lower.contains("blind") || lower.contains("vision") || lower.contains("one") || lower.contains("first") -> "BLIND"
                    lower.contains("deaf") || lower.contains("hearing") || lower.contains("hear") || lower.contains("two") || lower.contains("second") -> "DEAF"
                    lower.contains("speech") || lower.contains("talk") || lower.contains("speak") || lower.contains("three") || lower.contains("third") -> "SPEECH"
                    lower.contains("mobility") || lower.contains("wheelchair") || lower.contains("wheel") || lower.contains("chair") || lower.contains("four") || lower.contains("fourth") -> "MOBILITY"
                    else -> null
                }
                if (match != null) {
                    Log.d("LIMITLESS_TRACE", "Matched Route (\"$match-home\")")
                    savePersonaAndNavigate(match, navController, database, coroutineScope)
                } else {
                    speakPrompt("Sorry, I didn't recognise that. Please say Blind, Deaf, Speech, or Mobility.")
                }
            },
            onRetry = { error ->
                if (error == 11) {
                    Log.d("LIMITLESS_TRACE", "[Stage 2] Auto-restarting due to ERROR_11 after 500ms")
                    coroutineScope.launch(Dispatchers.Main) {
                        delay(500)
                        startPersonaListening()
                    }
                } else {
                    speakPrompt("I didn't catch that. Please say one of: Blind, Deaf, Speech, or Mobility.")
                }
            }
        )
        startListening(personaListener)
    }

    // ── Lifecycle: init TTS + SpeechRecognizer (UI thread) ───────────────────
    DisposableEffect(Unit) {
        recognizerRef = SpeechRecognizer.createSpeechRecognizer(context)
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.US
                ttsInstance?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == "welcome" || utteranceId == "prompt") {
                            Log.d("LIMITLESS_TRACE", "TTS prompt STARTED")
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        Log.d("LIMITLESS_TRACE", "TTS prompt DONE at ${System.currentTimeMillis()}")
                        ttsFinished = true
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        ttsFinished = true
                    }
                })
                // Triggers the initial LaunchedEffect
                ttsRef = ttsInstance
            }
        }

        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
            Log.d("LIMITLESS_TRACE", "[PersonaSelectScreen] Destroy SpeechRecognizer on dispose")
            recognizerRef?.destroy()
            recognizerRef = null
            ttsRef = null
        }
    }

    val wasReturning = remember { hasCompletedNameOnDisk }

    // ── Issue Prompts when Stage Changes ──────────────────────────────────────
    LaunchedEffect(isNameStage, ttsRef) {
        if (ttsRef == null) return@LaunchedEffect
        delay(500) // Small delay for TTS engine to initialize or transition
        
        if (isNameStage) {
            nameStrikes = 0
            speakPrompt("Welcome to Limitless. What is your name?")
        } else {
            val greeting = if (wasReturning) {
                "Choose your assist mode: Blind and Low Vision, Deaf and Hard of Hearing, Speech Impaired, or Mobility and Wheelchair."
            } else {
                "Hello $capturedName. Choose your assist mode: Blind and Low Vision, Deaf and Hard of Hearing, Speech Impaired, or Mobility and Wheelchair."
            }
            speakPrompt(greeting)
        }
    }

    // ── Open Mic Only After TTS Finishes ──────────────────────────────────────
    LaunchedEffect(ttsFinished) {
        if (ttsFinished) {
            delay(1500) // Brief pause after TTS ends to prevent feedback loop and wait for HAL
            if (isNameStage) {
                startNameCapture()
            } else {
                startPersonaListening()
            }
        }
    }

    // ── Root layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 24.dp, vertical = 32.dp),
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
                    listeningHint = listeningHint,
                    onMicTap = { startNameCapture() }
                )
            } else {
                PersonaSelectionStage(
                    capturedName = capturedName,
                    isListening = isListening,
                    listeningHint = listeningHint,
                    onMicTap = {
                        startPersonaListening()
                    },
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
    listeningHint: String,
    onMicTap: () -> Unit
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
                .background(DarkCharcoal, RoundedCornerShape(4.dp))
                .border(1.dp, PureWhite, RoundedCornerShape(4.dp))
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
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = PureWhite,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.semantics { heading() }
                )

                Text(
                    text = "Speak your name when the mic activates.",
                    fontSize = 14.sp,
                    color = NeutralGray,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Mic status indicator
        MicStatusIndicator(isListening = isListening, hint = listeningHint, onMicTap = onMicTap)
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
    onMicTap: () -> Unit,
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
                   else "Hello, $capturedName",
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            color = PureWhite,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.semantics { heading() }
        )

        Text(
            text = "TAP A MODE OR SPEAK YOUR CHOICE",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            color = NeutralGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Persona Cards Grid — 2x2 brutalist layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonaCard(
                    title = "BLIND\n& LOW-VISION",
                    color = PersonaBlind,
                    icon = Icons.Default.Visibility,
                    onClick = { onPersonaSelected("BLIND") },
                    modifier = Modifier.weight(1f)
                )
                PersonaCard(
                    title = "DEAF\n& HARD-OF-HEARING",
                    color = PersonaDeaf,
                    icon = Icons.Default.Hearing,
                    onClick = { onPersonaSelected("DEAF") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonaCard(
                    title = "SPEECH\nIMPAIRED",
                    color = PersonaSpeech,
                    icon = Icons.Default.QuestionAnswer,
                    onClick = { onPersonaSelected("SPEECH") },
                    modifier = Modifier.weight(1f)
                )
                PersonaCard(
                    title = "MOBILITY\n& WHEELCHAIR",
                    color = PersonaMobility,
                    icon = Icons.Default.Accessible,
                    onClick = { onPersonaSelected("MOBILITY") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mic status indicator
        MicStatusIndicator(isListening = isListening, hint = listeningHint, onMicTap = onMicTap)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared sub-components
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pulsing mic icon with a hint label. Tap to retry.
 */
@Composable
private fun MicStatusIndicator(isListening: Boolean, hint: String, onMicTap: () -> Unit) {
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
                .background(PureWhite, CircleShape)
                .border(1.dp, SubtleDivider, CircleShape)
                .clickable { onMicTap() }
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = PureBlack,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = hint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            color = NeutralGray,
            textAlign = TextAlign.Center
        )
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
            .height(132.dp)
            .background(DarkCharcoal, RoundedCornerShape(4.dp))
            .border(
                width = 1.dp,
                color = PureWhite,
                shape = RoundedCornerShape(4.dp)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = LimitlessTypography.titleSmall,
                color = PureWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
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
    database: LimitlessDatabase,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            database.personaPreferenceDao().setPreference(
                PersonaPreferenceEntity(
                    id = 1,
                    selectedPersona = persona,
                    updatedAt = System.currentTimeMillis()
                )
            )
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