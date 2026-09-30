package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.teamdexters.limitless.ui.blind.*
import com.teamdexters.limitless.ui.theme.*
import com.teamdexters.limitless.util.NetworkStatusTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blind & Low-Vision Assistant v2 Home Screen.
 * Features always-on camera with real-time object narration.
 */
@Composable
fun BlindHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Vision narration loading state
    var isDescribing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera permission handling
    val cameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Haptic indicator state
    var hapticActive by remember { mutableStateOf(false) }
    val hapticAlpha by animateFloatAsState(
        targetValue = if (hapticActive) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "haptic_alpha"
    )

    // Managers
    val ttsManager = remember { TTSManager(context) }
    val objectDetector = remember { RealTimeObjectDetector(context) }
    val objectNarrator = remember {
        ObjectNarrator(
            ttsManager = ttsManager,
            scope = scope,
            context = context,
            onHapticTriggered = {
                hapticActive = true
                scope.launch {
                    delay(200)
                    hapticActive = false
                }
            }
        )
    }
    val networkStatusTracker = remember { NetworkStatusTracker(context) }

    // Object detection state
    var latestFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var latestRotation by remember { mutableStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }

    // Result banner state
    var resultText by remember { mutableStateOf("") }
    var showResultBanner by remember { mutableStateOf(false) }

    // Initialize TTS and Object Detector
    LaunchedEffect(Unit) {
        ttsManager.initialize { success ->
            if (!success) {
                Toast.makeText(context, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }

        val objectDetectorInitialized = objectDetector.initialize()
        if (!objectDetectorInitialized) {
            Toast.makeText(context, "Object detector initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        networkStatusTracker.register()
        VisionNarrationClient.registerNetworkProvider(networkStatusTracker)
        VisionNarrationClient.setObjectDetector(objectDetector)
        BlindV2Actions.registerSceneNarrationCallback {
            triggerVisionNarration(
                frame = latestFrame,
                rotation = latestRotation,
                ttsManager = ttsManager,
                scope = scope,
                onResult = { text ->
                    resultText = text
                    showResultBanner = true
                    scope.launch {
                        delay(8000) // Auto fade out after 8s for longer descriptions
                        showResultBanner = false
                    }
                },
                setIsDescribing = { isDescribing = it },
                context = context
            )
        }
        onDispose {
            ttsManager.release()
            objectDetector.close()
            networkStatusTracker.unregister()
        }
    }

    // Real-time object detection loop (800ms throttled)
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                delay(800) // 800ms throttling as required
                
                val frame = latestFrame
                val rotation = latestRotation
                
                if (frame != null) {
                    // Update VisionNarrationClient with latest frame
                    VisionNarrationClient.updateLatestFrame(frame, rotation)

                    // Detect objects with rotation handling
                    val objects = objectDetector.detectObjects(frame, rotation)

                    // Narrate only new objects with haptic feedback
                    objectNarrator.narrateObjects(objects, frame.width, frame.height)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {
        if (cameraPermission) {
            // Full-screen camera preview (base layer)
            BlindCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onFrameReady = { bitmap, rotation ->
                    latestFrame = bitmap
                    latestRotation = rotation
                },
                onError = { exception ->
                    Toast.makeText(context, "Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            )

            // Top overlay: "Blind Assist" pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    color = SurfaceTint.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = "Blind Assist screen"
                        }
                ) {
                    Text(
                        text = "Blind Assist",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = TextPrimary
                    )
                }
            }

            // Result banner (fade in/out)
            AnimatedVisibility(
                visible = showResultBanner && resultText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Surface(
                        color = HighlightBox,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .semantics {
                                contentDescription = "Result: $resultText"
                            }
                    ) {
                        Text(
                            text = resultText,
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Visual haptic indicator (subtle pulse when haptic feedback is active)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(hapticAlpha)
                    .background(PersonaBlind.copy(alpha = 0.15f))
                    .semantics {
                        contentDescription = "Haptic feedback active"
                    }
            )

            // Bottom overlay: action buttons
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Describe My Surroundings button (Phase 3)
                    Button(
                        onClick = {
                            if (!isDescribing && latestFrame != null) {
                                triggerVisionNarration(
                                    frame = latestFrame,
                                    rotation = latestRotation,
                                    ttsManager = ttsManager,
                                    scope = scope,
                                    onResult = { text ->
                                        resultText = text
                                        showResultBanner = true
                                        scope.launch {
                                            delay(8000) // Auto fade out after 8s for longer descriptions
                                            showResultBanner = false
                                        }
                                    },
                                    setIsDescribing = { isDescribing = it },
                                    context = context
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .semantics {
                                contentDescription = "Describe my surroundings. Double tap to hear a rich description of what the camera sees."
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PersonaBlind,
                            contentColor = TextPrimary
                        ),
                        border = BorderStroke(2.dp, TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isDescribing
                    ) {
                        Text(
                            text = if (isDescribing) "Describing..." else "Describe My Surroundings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Read Text button (placeholder for later phase)
                        Button(
                            onClick = {
                                // Placeholder for Phase 2
                                Toast.makeText(context, "Read Text coming in Phase 2", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .semantics {
                                    contentDescription = "Read Text. Double tap to activate. Coming in Phase 2."
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Read Text",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Describe button (placeholder for later phase)
                        Button(
                            onClick = {
                                // Placeholder for Phase 3
                                Toast.makeText(context, "Describe coming in Phase 3", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .semantics {
                                    contentDescription = "Describe. Double tap to activate. Coming in Phase 3."
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Describe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Permission denied message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * Trigger vision narration for the current camera frame.
 * Handles loading state, API call, TTS, and visual feedback.
 */
private fun triggerVisionNarration(
    frame: android.graphics.Bitmap?,
    rotation: Int,
    ttsManager: TTSManager,
    scope: CoroutineScope,
    onResult: (String) -> Unit,
    setIsDescribing: (Boolean) -> Unit,
    context: android.content.Context
) {
    if (frame == null) return

    scope.launch {
        setIsDescribing(true)

        try {
            val result = VisionNarrationClient.describeScene(frame, rotation)

            result.onSuccess { description ->
                // Speak the response
                ttsManager.speak(description)

                // Fire soft haptic pulse (OneShot 150ms)
                HapticVocabulary.triggerOneShot(context, 150)

                // Show in result banner
                onResult(description)
            }
        } finally {
            setIsDescribing(false)
        }
    }
}
