package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.teamdexters.limitless.blind.BlindAIInvoker
import com.teamdexters.limitless.blind.BlindNavigationVoice
import com.teamdexters.limitless.blind.BlindVoiceOrchestrator
import com.teamdexters.limitless.blind.CameraFrameManager
import com.teamdexters.limitless.blind.OfflineTextReader
import com.teamdexters.limitless.ui.blind.*
import com.teamdexters.limitless.ui.blind.nav.*
import com.teamdexters.limitless.ui.theme.*
import com.teamdexters.limitless.util.NetworkStatusTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blind & Low-Vision Assistant — Voice-First Home Screen (v4).
 *
 * Primary interaction: fully hands-free via continuous SpeechRecognizer loop.
 * Accessibility backup: large Mic FAB at bottom (TalkBack labelled).
 *
 * Core capabilities (voice-only, no button dependency):
 *   1. Describe surroundings
 *   2. Object detection
 *   3. Color detection
 *   4. Read text / OCR
 *
 * Navigation (Start / Stop) still available via voice and a single button.
 */
@Composable
fun BlindHomeScreen() {
    val context     = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope       = rememberCoroutineScope()

    // ── Permissions ────────────────────────────────────────────────────────────
    var cameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var micPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val transcriptEntries = remember { mutableStateListOf<Pair<String, String>>() }
    val ttsManager = remember { TTSManager(context) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        cameraPermission = results[Manifest.permission.CAMERA] == true
        micPermission    = results[Manifest.permission.RECORD_AUDIO] == true
        if (!cameraPermission) {
            Log.w("LIMITLESS_TRACE", "BlindHomeScreen: Camera permission denied")
            ttsManager.speak("Camera permission needed for vision features.")
        }
        if (!micPermission) {
            Log.w("LIMITLESS_TRACE", "BlindHomeScreen: Mic permission denied")
            ttsManager.speak("Microphone permission needed for voice commands.")
        }
    }

    LaunchedEffect(Unit) {
        val permsNeeded = buildList {
            if (!cameraPermission) add(Manifest.permission.CAMERA)
            if (!micPermission)    add(Manifest.permission.RECORD_AUDIO)
        }
        if (permsNeeded.isNotEmpty()) {
            permLauncher.launch(permsNeeded.toTypedArray())
        }
    }

    // ── Core managers ──────────────────────────────────────────────────────────
    val objectDetector   = remember { RealTimeObjectDetector(context) }
    val objectNarrator   = remember {
        ObjectNarrator(
            ttsManager = ttsManager,
            scope = scope,
            context = context,
            onHapticTriggered = null
        )
    }
    val networkTracker   = remember { NetworkStatusTracker(context) }
    val pathDetector     = remember { PathFeatureDetectorV2(context) }

    // ── UI State ───────────────────────────────────────────────────────────────
    var orchestratorState by remember { mutableStateOf(BlindVoiceOrchestrator.OrchestratorState.IDLE) }
    var latestFrame       by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var latestRotation    by remember { mutableStateOf(0) }
    var showNavOverlay    by remember { mutableStateOf(false) }
    var currentRoute      by remember { mutableStateOf<MockRoute?>(null) }
    var pathWarningIcon   by remember { mutableStateOf<PathFeatureType?>(null) }
    var showPathWarning   by remember { mutableStateOf(false) }
    val pathWarningAlpha  by animateFloatAsState(
        targetValue = if (showPathWarning) 1f else 0f,
        animationSpec = tween(200), label = "path_alpha"
    )

    // ── Orchestrator (voice-only loop) ────────────────────────────────────────
    val orchestrator = remember {
        BlindVoiceOrchestrator(
            context = context,
            ttsManager = ttsManager,
            scope = scope,
            onStateChanged = { state ->
                orchestratorState = state
                Log.d("LIMITLESS_TRACE", "BlindHomeScreen: OrchestratorState=$state")
            },
            onResult = { userCmd, text ->
                // Clear old transcript text when a new request is processed
                transcriptEntries.clear()
                transcriptEntries.add(Pair(userCmd, text))
            }
        )
    }

    // ── Init + greeting + start voice loop ────────────────────────────────────
    LaunchedEffect(Unit) {
        ttsManager.initialize { success ->
            if (!success) {
                Log.e("LIMITLESS_TRACE", "BlindHomeScreen: TTS init failed")
            }
        }
        objectDetector.initialize()
        pathDetector.initialize(null)
        BlindAIInvoker.initialize(context)
        BlindNavigationVoice.initialize(context)

        // Greeting — give TTS 500ms to be ready
        delay(500)
        ttsManager.speakAndWait(
            "Blind assist ready. Ask me to describe surroundings, detect color, " +
            "find objects, or read text."
        )

        // Auto-start voice loop if mic permission is granted
        if (micPermission) {
            delay(600)
            orchestrator.start()
        }
    }

    // ── Update orchestrator when mic permission is granted late ───────────────
    LaunchedEffect(micPermission) {
        if (micPermission && orchestratorState == BlindVoiceOrchestrator.OrchestratorState.IDLE) {
            orchestrator.start()
        }
    }

    // ── Dispose ───────────────────────────────────────────────────────────────
    DisposableEffect(Unit) {
        networkTracker.register()
        VisionNarrationClient.registerNetworkProvider(networkTracker)
        VisionNarrationClient.setObjectDetector(objectDetector)
        onDispose {
            orchestrator.stop()
            ttsManager.release()
            objectDetector.close()
            networkTracker.unregister()
            BlindAIInvoker.release()
        }
    }

    // ── Real-time object narration loop (800ms throttle) ──────────────────────
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                delay(800)
                val (frame, rotation) = CameraFrameManager.getLatestFrame()
                latestFrame   = frame
                latestRotation = rotation
                VisionNarrationClient.updateLatestFrame(frame, rotation)
                val objects = objectDetector.detectObjects(frame, rotation)
                objectNarrator.narrateObjects(objects, frame.width, frame.height)
            }
        }
    }

    // ── Path feature detection loop (1000ms throttle) ─────────────────────────
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                delay(1000)
                val (frame, rotation) = CameraFrameManager.getLatestFrame()
                if (pathDetector.isReady()) {
                    val features = pathDetector.detectPathFeatures(
                        bitmap = frame,
                        rotationDegrees = rotation,
                        frameWidth = frame.width,
                        frameHeight = frame.height,
                        ttsManager = ttsManager,
                        scope = scope
                    )
                    for (feature in features) {
                        val hapticEvent = when (feature.type) {
                            PathFeatureType.RAMP          -> HapticVocabulary.HapticEvent.RAMP_DETECTED
                            PathFeatureType.STAIRS        -> HapticVocabulary.HapticEvent.STAIRS_DETECTED
                            PathFeatureType.CURB          -> HapticVocabulary.HapticEvent.OBSTACLE_NEAR
                            PathFeatureType.ZEBRA_CROSSING -> HapticVocabulary.HapticEvent.OBJECT_CENTER
                            PathFeatureType.TRAFFIC_LIGHT  -> HapticVocabulary.HapticEvent.OBJECT_CENTER
                        }
                        HapticVocabulary.trigger(context, hapticEvent)
                        pathWarningIcon = feature.type
                        showPathWarning = true
                        scope.launch {
                            repeat(3) { delay(200); showPathWarning = false; delay(200); showPathWarning = true }
                            delay(200); showPathWarning = false; pathWarningIcon = null
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════════════════════════
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {
        if (cameraPermission) {

            // ── Layer 0: Full-screen camera preview ────────────────────────────
            BlindCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onFrameReady = { bmp, rot ->
                    latestFrame    = bmp
                    latestRotation = rot
                    Log.d("LIMITLESS_TRACE", "BlindHomeScreen: Frame updated ${bmp.width}x${bmp.height}")
                },
                onError = { ex ->
                    Log.e("LIMITLESS_TRACE", "BlindHomeScreen: CameraX error", ex)
                    ttsManager.speak("Unable to access camera hardware.")
                }
            )

            // ── Layer 1: Status chip (top-center) ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                StatusChip(state = orchestratorState)
            }

            // ── Layer 2: Path warning icon (flashing, below chip) ──────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 84.dp)
                    .alpha(pathWarningAlpha),
                contentAlignment = Alignment.TopCenter
            ) {
                if (pathWarningIcon != null) {
                    PathFeatureIcon(
                        featureType = pathWarningIcon!!,
                        modifier = Modifier.size(96.dp),
                        color = TextPrimary
                    )
                }
            }

            // ── Layer 3: Live Spoken Transcript Card ───────────────────
            if (transcriptEntries.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 200.dp, start = 16.dp, end = 16.dp, top = 180.dp) // Leave space for bottom controls & top chips
                        .fillMaxWidth()
                ) {
                    Surface(
                        color = HighlightBox.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, TextPrimary),
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = "Live spoken text paragraph transcript"
                                liveRegion = LiveRegionMode.Polite
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header with Clear button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { transcriptEntries.clear() },
                                    modifier = Modifier.height(56.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PersonaBlind,
                                        contentColor = TextPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Clear Text", fontWeight = FontWeight.Bold, color = TextPrimary)
                                }
                            }

                            // Scrollable paragraph
                            val scrollState = rememberScrollState()
                            LaunchedEffect(transcriptEntries.size) {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .verticalScroll(scrollState)
                            ) {
                                val transcriptText = transcriptEntries.joinToString(separator = "\n\n") { (cmd, res) ->
                                    val cmdText = if (cmd.isNotBlank()) "You said: '$cmd'\n" else ""
                                    "${cmdText}Assistant: $res"
                                }
                                Text(
                                    text = transcriptText,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        lineHeight = 26.sp
                                    ),
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default, // Assuming Manrope is default, per limits
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            // ── Layer 4: Bottom controls ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Start Navigation (accessibility shortcut — nav still needs explicit destination)
                    Button(
                        onClick = {
                            startNavigation(
                                destination = "Main Entrance",
                                setRoute = { currentRoute = it },
                                setShowOverlay = { showNavOverlay = it }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .semantics {
                                contentDescription = "Start Navigation. Double tap to begin turn-by-turn navigation."
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PersonaBlind,
                            contentColor = TextPrimary
                        ),
                        border = BorderStroke(2.dp, TextPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Start Navigation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    // Large Mic FAB — accessibility backup for core voice features
                    MicFab(
                        state = orchestratorState,
                        onClick = {
                            if (!micPermission) {
                                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            } else {
                                orchestrator.triggerManualListen()
                            }
                        }
                    )
                }
            }

            // ── Layer 5: Navigation overlay ────────────────────────────────────
            if (showNavOverlay && currentRoute != null) {
                NavigationOverlay(
                    destination = currentRoute!!.destination,
                    route = currentRoute!!,
                    ttsManager = ttsManager,
                    context = context,
                    onExit = {
                        showNavOverlay = false
                        currentRoute   = null
                    }
                )
            }

        } else {
            // ── No camera permission ───────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Camera permission required for vision features.",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Button(
                        onClick = {
                            permLauncher.launch(
                                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                            )
                        },
                        modifier = Modifier
                            .height(72.dp)
                            .semantics { contentDescription = "Grant permissions. Double tap to allow camera and microphone." },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PersonaBlind,
                            contentColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Permissions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

// ── Status Chip ────────────────────────────────────────────────────────────────

@Composable
private fun StatusChip(state: BlindVoiceOrchestrator.OrchestratorState) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    val (label, dotColor) = when (state) {
        BlindVoiceOrchestrator.OrchestratorState.LISTENING -> "Listening" to Color(0xFF4CAF50)
        BlindVoiceOrchestrator.OrchestratorState.THINKING  -> "Thinking"  to Color(0xFFFFA726)
        BlindVoiceOrchestrator.OrchestratorState.SPEAKING  -> "Speaking"  to PersonaBlind
        BlindVoiceOrchestrator.OrchestratorState.IDLE      -> "Tap mic to talk" to Color(0xFF9E9E9E)
    }
    val dotAlpha = if (state == BlindVoiceOrchestrator.OrchestratorState.LISTENING ||
                       state == BlindVoiceOrchestrator.OrchestratorState.THINKING) pulseAlpha else 1f

    Surface(
        color = SurfaceTint.copy(alpha = 0.90f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.semantics { contentDescription = "Status: $label" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(dotAlpha)
                    .background(dotColor, CircleShape)
            )
            Text(
                text = label,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

// ── Mic FAB ────────────────────────────────────────────────────────────────────

@Composable
private fun MicFab(
    state: BlindVoiceOrchestrator.OrchestratorState,
    onClick: () -> Unit
) {
    val isListening = state == BlindVoiceOrchestrator.OrchestratorState.LISTENING
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = if (isListening) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "mic_scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .size(80.dp)
            .scale(scale)
            .semantics {
                contentDescription =
                    if (isListening) "Listening. Tap to stop and re-arm."
                    else "Tap to talk. Voice commands: describe surroundings, detect color, find objects, read text."
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isListening) SurfaceTint else PersonaBlind,
            contentColor = TextPrimary
        ),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Flat 2D mic icon drawn with Canvas — no emoji, no Image/vector resource dependency
        androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx())
            val micColor = TextPrimary

            // Mic capsule body (rounded rect)
            drawRoundRect(
                color = micColor,
                topLeft = androidx.compose.ui.geometry.Offset(cx - 6.dp.toPx(), cy - 12.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 16.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                style = stroke
            )
            // Mic stand arc (bottom semicircle)
            drawArc(
                color = micColor,
                startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(cx - 10.dp.toPx(), cy - 4.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(20.dp.toPx(), 14.dp.toPx()),
                style = stroke
            )
            // Vertical stem
            drawLine(
                color = micColor,
                start = androidx.compose.ui.geometry.Offset(cx, cy + 10.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(cx, cy + 14.dp.toPx()),
                strokeWidth = 3.5.dp.toPx()
            )
            // Horizontal base
            drawLine(
                color = micColor,
                start = androidx.compose.ui.geometry.Offset(cx - 6.dp.toPx(), cy + 14.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(cx + 6.dp.toPx(), cy + 14.dp.toPx()),
                strokeWidth = 3.5.dp.toPx()
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun startNavigation(
    destination: String,
    setRoute: (MockRoute) -> Unit,
    setShowOverlay: (Boolean) -> Unit
) {
    val route = MockRouter.getRoute(destination)
    setRoute(route)
    setShowOverlay(true)
}
