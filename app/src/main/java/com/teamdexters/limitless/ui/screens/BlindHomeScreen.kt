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
import androidx.room.Room
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.ui.blind.*
import com.teamdexters.limitless.ui.theme.*
import com.teamdexters.limitless.util.NetworkStatusTracker

/**
 * Blind & Low-Vision Assistant home screen.
 * Provides OCR text reading, color detection, object detection, path feature detection, and landmark tagging/recognition modes with camera integration.
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

    val locationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Location permission optional for landmark tagging", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        if (!locationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Mode state: "ocr", "color", "describe", "path", "tag", or "recognize"
    var currentMode by remember { mutableStateOf("ocr") }

    // Richer descriptions toggle state
    var useRicherDescriptions by remember { mutableStateOf(false) }

    // Landmark tagging state
    var showTaggingDialog by remember { mutableStateOf(false) }
    var landmarkName by remember { mutableStateOf("") }
    var isListeningForSpeech by remember { mutableStateOf(false) }

    // Managers
    val ttsManager = remember { TTSManager(context) }
    val ocrManager = remember { OCRManager() }
    val colorDetector = remember { ColorDetector() }
    val objectDetector = remember { ObjectDetector(context) }
    val sceneDescriptionBuilder = remember { SceneDescriptionBuilder() }
    val pathFeatureDetector = remember { PathFeatureDetector(context) }
    val pathFeatureDescriptionBuilder = remember { PathFeatureDescriptionBuilder() }
    
    // Network and Gemini
    val networkStatusTracker = remember { NetworkStatusTracker(context) }
    val geminiClient = remember { GeminiClient() }

    // Landmark tagging and recognition
    val database = remember {
        Room.databaseBuilder(
            context,
            LimitlessDatabase::class.java,
            "limitless_database"
        ).build()
    }
    val landmarkTagger = remember { LandmarkTagger(context, database.taggedLocationDao()) }
    val landmarkSpeechRecognizer = remember { LandmarkSpeechRecognizer(context) }
    val landmarkRecognizer = remember { LandmarkRecognizer(database.taggedLocationDao(), landmarkTagger.getAllSignatures()) }

    // Result state
    var resultText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Initialize TTS, Object Detector, Path Feature Detector, and Network Tracker
    LaunchedEffect(Unit) {
        ttsManager.initialize { success ->
            if (!success) {
                Toast.makeText(context, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }

        val objectDetectorInitialized = objectDetector.initialize()
        // Note: Object detector may fail to initialize if model is not available
        // This is handled gracefully - the detector will return empty results

        val pathFeatureDetectorInitialized = pathFeatureDetector.initialize()
        // Note: Path feature detector may fail to initialize if model is not available
        // This is handled gracefully - the detector will return empty results

        networkStatusTracker.register()
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.release()
            ocrManager.close()
            objectDetector.close()
            pathFeatureDetector.close()
            networkStatusTracker.unregister()
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
            // Mode toggle buttons in 2x3 grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // First row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = "Read Text",
                        isActive = currentMode == "ocr",
                        onClick = { currentMode = "ocr" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = "Detect Color",
                        isActive = currentMode == "color",
                        onClick = { currentMode = "color" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = "Describe",
                        isActive = currentMode == "describe",
                        onClick = { currentMode = "describe" },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Second row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = "Path",
                        isActive = currentMode == "path",
                        onClick = { currentMode = "path" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = "Tag Place",
                        isActive = currentMode == "tag",
                        onClick = { currentMode = "tag" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeButton(
                        text = "Recognize",
                        isActive = currentMode == "recognize",
                        onClick = { currentMode = "recognize" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Richer descriptions toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = useRicherDescriptions,
                    onCheckedChange = { useRicherDescriptions = it },
                    modifier = Modifier.semantics {
                        contentDescription = if (useRicherDescriptions) 
                            "Richer descriptions enabled" 
                        else 
                            "Richer descriptions disabled"
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PersonaBlind,
                        uncheckedThumbColor = SurfaceTint,
                        checkedTrackColor = PersonaBlind,
                        uncheckedTrackColor = SurfaceTint
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Richer descriptions (online)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle for richer descriptions using online AI"
                    }
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

            // Landmark tagging dialog
            if (showTaggingDialog) {
                LandmarkTaggingDialog(
                    landmarkName = landmarkName,
                    onNameChange = { landmarkName = it },
                    isListening = isListeningForSpeech,
                    onStartSpeech = {
                        isListeningForSpeech = true
                        landmarkSpeechRecognizer.startRecognition(
                            onResult = { recognizedText ->
                                landmarkName = recognizedText
                                isListeningForSpeech = false
                            },
                            onError = { error ->
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                isListeningForSpeech = false
                            }
                        )
                    },
                    onStopSpeech = {
                        landmarkSpeechRecognizer.stopRecognition()
                        isListeningForSpeech = false
                    },
                    onConfirm = {
                        if (landmarkName.isNotBlank()) {
                            val frame = latestFrame
                            if (frame != null) {
                                isProcessing = true
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                    try {
                                        landmarkTagger.tagLocation(
                                            name = landmarkName,
                                            photo = frame,
                                            onSuccess = { tagId ->
                                                resultText = "Place tagged: $landmarkName"
                                                ttsManager.speak("Place tagged: $landmarkName")
                                                landmarkName = ""
                                                showTaggingDialog = false
                                            },
                                            onError = { error ->
                                                resultText = "Failed to tag place"
                                                ttsManager.speak("Failed to tag place")
                                            }
                                        )
                                    } catch (e: Exception) {
                                        resultText = "Failed to tag place"
                                        ttsManager.speak("Failed to tag place")
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Camera not ready", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancel = {
                        landmarkName = ""
                        showTaggingDialog = false
                        landmarkSpeechRecognizer.stopRecognition()
                        isListeningForSpeech = false
                    }
                )
            }

            // Action button
            ActionButton(
                text = when (currentMode) {
                    "ocr" -> "Capture & Read"
                    "color" -> "Detect Color"
                    "describe" -> "Describe Surroundings"
                    "path" -> "Detect Path Features"
                    "tag" -> "Tag This Place"
                    "recognize" -> "Recognize Place"
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

                    when (currentMode) {
                        "tag" -> {
                            // Landmark tagging mode
                            showTaggingDialog = true
                            ttsManager.speak("Say the name of this place")
                        }
                        "recognize" -> {
                            // Landmark recognition mode
                            isProcessing = true
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                try {
                                    val result = landmarkRecognizer.recognizeLandmark(frame)
                                    when (result) {
                                        is LandmarkRecognizer.RecognitionResult.Success -> {
                                            resultText = "You are near: ${result.locationName}"
                                            ttsManager.speak("You are near: ${result.locationName}")
                                        }
                                        is LandmarkRecognizer.RecognitionResult.NoMatch -> {
                                            resultText = "No tagged place recognized"
                                            ttsManager.speak("No tagged place recognized")
                                        }
                                        is LandmarkRecognizer.RecognitionResult.Error -> {
                                            resultText = "Error recognizing place"
                                            ttsManager.speak("Error recognizing place")
                                        }
                                    }
                                } catch (e: Exception) {
                                    resultText = "Error recognizing place"
                                    ttsManager.speak("Error recognizing place")
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                        else -> {
                            // Other modes require processing state
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
                                            
                                            // Use Gemini if toggle is ON and network is available
                                            if (useRicherDescriptions && networkStatusTracker.isCurrentlyOnline()) {
                                                try {
                                                    // Build object list for Gemini
                                                    val objectList = objects.joinToString(", ") { "${it.label} (confidence: ${it.confidence})" }
                                                    val prompt = "Describe this scene for a blind person. Detected objects: $objectList. Give a brief, helpful description in 1-2 sentences."
                                                    
                                                    // 5 second timeout for Gemini
                                                    val geminiResult = kotlinx.coroutines.withTimeoutOrNull(5000) {
                                                        geminiClient.queryGemini(prompt)
                                                    }
                                                    
                                                    if (geminiResult != null && geminiResult.isSuccess) {
                                                        val richDescription = geminiResult.getOrNull() ?: ""
                                                        if (richDescription.isNotBlank()) {
                                                            resultText = richDescription
                                                            ttsManager.speak(richDescription)
                                                        } else {
                                                            // Fallback to rule-based
                                                            val description = sceneDescriptionBuilder.buildDescription(objects, frame.width)
                                                            resultText = description
                                                            ttsManager.speak(description)
                                                        }
                                                    } else {
                                                        // Fallback to rule-based on timeout or error
                                                        val description = sceneDescriptionBuilder.buildDescription(objects, frame.width)
                                                        resultText = description
                                                        ttsManager.speak(description)
                                                    }
                                                } catch (e: Exception) {
                                                    // Fallback to rule-based on any error
                                                    val description = sceneDescriptionBuilder.buildDescription(objects, frame.width)
                                                    resultText = description
                                                    ttsManager.speak(description)
                                                }
                                            } else {
                                                // Use rule-based description
                                                val description = sceneDescriptionBuilder.buildDescription(objects, frame.width)
                                                resultText = description
                                                ttsManager.speak(description)
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error detecting objects"
                                            ttsManager.speak("Error detecting objects")
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                                "path" -> {
                                    // Path feature detection mode
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        try {
                                            if (!pathFeatureDetector.isReady()) {
                                                resultText = "Path detection model not loaded"
                                                ttsManager.speak("Path detection model not loaded")
                                            } else {
                                                val features = pathFeatureDetector.detectPathFeatures(frame)
                                                val description = pathFeatureDescriptionBuilder.buildDescription(features)
                                                resultText = description
                                                ttsManager.speak(description)
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error detecting path features"
                                            ttsManager.speak("Error detecting path features")
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
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

/**
 * Dialog for landmark tagging with speech and text input.
 */
@Composable
private fun LandmarkTaggingDialog(
    landmarkName: String,
    onNameChange: (String) -> Unit,
    isListening: Boolean,
    onStartSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Tag This Place",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Say or type the name of this place",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                
                OutlinedTextField(
                    value = landmarkName,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = "Landmark name input field"
                        },
                    placeholder = { Text("Enter place name") },
                    singleLine = true
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = if (isListening) onStopSpeech else onStartSpeech,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) PersonaBlind else SurfaceTint,
                            contentColor = TextPrimary
                        )
                    ) {
                        if (isListening) {
                            Text("Stop Listening")
                        } else {
                            Text("Speak")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = landmarkName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersonaBlind,
                    contentColor = TextPrimary
                )
            ) {
                Text("Tag")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = TextPrimary)
            }
        }
    )
}