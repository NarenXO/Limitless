package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.teamdexters.limitless.ui.blind.*
import com.teamdexters.limitless.ui.theme.*

/**
 * Blind & Low-Vision Assistant home screen.
 * Provides OCR text reading, color detection, and object detection modes with camera integration.
 */
@Composable
fun BlindHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    // Mode state: "ocr", "color", or "describe"
    var currentMode by remember { mutableStateOf("ocr") }

    // Managers
    val ttsManager = remember { TTSManager(context) }
    val ocrManager = remember { OCRManager() }
    val colorDetector = remember { ColorDetector() }
    val objectDetector = remember { ObjectDetector(context) }
    val sceneDescriptionBuilder = remember { SceneDescriptionBuilder() }

    // Result state
    var resultText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Initialize TTS and Object Detector
    LaunchedEffect(Unit) {
        ttsManager.initialize { success ->
            if (!success) {
                Toast.makeText(context, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }

        val objectDetectorInitialized = objectDetector.initialize()
        // Note: Object detector may fail to initialize if model is not available
        // This is handled gracefully - the detector will return empty results
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.release()
            ocrManager.close()
            objectDetector.close()
        }
    }

    // Camera frame processing
    var latestFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {
        if (cameraPermission) {
            // Camera preview
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                showReticle = currentMode == "color",
                onFrameReady = { bitmap ->
                    latestFrame = bitmap
                },
                onError = { exception ->
                    Toast.makeText(context, "Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            )
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

        // UI overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode toggle buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Read Text button
                ModeButton(
                    text = "Read Text",
                    isActive = currentMode == "ocr",
                    onClick = { currentMode = "ocr" },
                    modifier = Modifier.weight(1f)
                )

                // Detect Color button
                ModeButton(
                    text = "Detect Color",
                    isActive = currentMode == "color",
                    onClick = { currentMode = "color" },
                    modifier = Modifier.weight(1f)
                )

                // Describe Surroundings button
                ModeButton(
                    text = "Describe",
                    isActive = currentMode == "describe",
                    onClick = { currentMode = "describe" },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Result banner
            if (resultText.isNotEmpty()) {
                ResultBanner(
                    text = resultText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            // Action button
            ActionButton(
                text = when (currentMode) {
                    "ocr" -> "Capture & Read"
                    "color" -> "Detect Color"
                    "describe" -> "Describe Surroundings"
                    else -> "Capture & Read"
                },
                isProcessing = isProcessing,
                onClick = {
                    if (isProcessing) return@ActionButton

                    val frame = latestFrame
                    if (frame == null) {
                        Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                        return@ActionButton
                    }

                    isProcessing = true

                    when (currentMode) {
                        "ocr" -> {
                            // OCR mode
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                try {
                                    val text = ocrManager.recognizeText(frame)
                                    if (text != null && text.isNotBlank()) {
                                        resultText = text
                                        ttsManager.speak(text)
                                    } else {
                                        resultText = "No text detected"
                                        ttsManager.speak("No text detected")
                                    }
                                } catch (e: Exception) {
                                    resultText = "Error reading text"
                                    ttsManager.speak("Error reading text")
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                        "color" -> {
                            // Color detection mode
                            try {
                                val colorName = colorDetector.detectColorAtCenter(frame)
                                resultText = "Color: $colorName"
                                ttsManager.speak(colorName)
                            } catch (e: Exception) {
                                resultText = "Error detecting color"
                                ttsManager.speak("Error detecting color")
                            } finally {
                                isProcessing = false
                            }
                        }
                        "describe" -> {
                            // Object detection mode
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                try {
                                    val objects = objectDetector.detectObjects(frame)
                                    val description = sceneDescriptionBuilder.buildDescription(objects, frame.width)
                                    resultText = description
                                    ttsManager.speak(description)
                                } catch (e: Exception) {
                                    resultText = "Error detecting objects"
                                    ttsManager.speak("Error detecting objects")
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Mode toggle button with TalkBack support.
 */
@Composable
private fun ModeButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp)
            .semantics {
                contentDescription = if (isActive) "$text mode active" else "Switch to $text mode"
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) PersonaBlind else SurfaceTint,
            contentColor = TextPrimary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Large action button with processing state.
 */
@Composable
private fun ActionButton(
    text: String,
    isProcessing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isProcessing,
        modifier = modifier
            .semantics {
                contentDescription = if (isProcessing) "Processing" else text
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = PersonaBlind,
            contentColor = TextPrimary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = TextPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
}

/**
 * Result banner showing OCR or color detection results.
 */
@Composable
private fun ResultBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = HighlightBox
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Result: $text"
                },
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
    }
}