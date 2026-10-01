package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.ui.blind.*
import com.teamdexters.limitless.ui.theme.*
import com.teamdexters.limitless.util.NetworkStatusTracker
import com.teamdexters.limitless.routing.engine.AccessibleRouter
import com.teamdexters.limitless.routing.model.AccessibilityFilter

import androidx.activity.compose.BackHandler
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack

/**
 * Blind & Low-Vision Assistant home screen.
 * Provides OCR text reading, color detection, object detection, path feature detection, and landmark tagging/recognition modes with camera integration.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecureKeyProviderEntryPoint {
    fun secureKeyProvider(): com.teamdexters.limitless.config.SecureKeyProvider
}

@Composable
fun BlindHomeScreen(
    onBack: () -> Unit = {},
    viewModel: com.teamdexters.limitless.ui.blind.BlindViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    // Collect the vision result state from the ViewModel
    val visionResult by viewModel.visionResult.collectAsState()

    // Intercept physical phone back gestures & hardware back buttons
    BackHandler(enabled = true) {
        android.util.Log.e("NAV_DEBUG", "System BackHandler triggered in BlindHomeScreen")
        onBack()
    }

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

    // Navigation state
    var showNavigationOverlay by remember { mutableStateOf(false) }
    val vibrationHelper = remember { VibrationHelper(context) }
    val accessibleRouter = remember { AccessibleRouter() }
    val currentRoute = remember { 
        accessibleRouter.findRoute(
            startNodeId = "KCG_MAIN_GATE",
            destinationNodeId = "LIBRARY_2ND_FLOOR",
            filter = AccessibilityFilter(requireRamp = true)
        )
    }

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
    val secureKeyProvider = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SecureKeyProviderEntryPoint::class.java
        ).secureKeyProvider()
    }
    val geminiClient = remember(secureKeyProvider) { GeminiClient(secureKeyProvider) }

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
    var showResultBanner by remember { mutableStateOf(false) }

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
    
    // Collect Vision Command
    LaunchedEffect(visionResult) {
        visionResult?.let {
            resultText = it
            showResultBanner = true
            ttsManager.speak(it)
            viewModel.clearResult()
        }
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
    var latestRotation by remember { mutableStateOf(0) }

    com.teamdexters.limitless.ui.components.LimitlessScreenFrame(
        title = "Blind & Low Vision",
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(PureWhite),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Blind & Low-Vision Assist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack,
                    fontSize = 22.sp,
                    modifier = Modifier.semantics {
                        contentDescription = "Blind and Low-Vision Assist screen"
                    }
                )
            }

            // Camera preview card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(LightGray, RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = SubtleDivider,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
            ) {
                if (cameraPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        showReticle = currentMode == "color",
                        onFrameReady = { bitmap ->
                            latestFrame = bitmap
                        },
                        onCaptureReady = { bitmap, rotation ->
                            latestFrame = bitmap
                            latestRotation = rotation
                        },
                        onError = { exception ->
                            Toast.makeText(context, "Camera error: ${exception.message}", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Camera permission required",
                            color = PureBlack,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode selector - 2-column responsive grid
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // First row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeChip(
                        text = "Read Text",
                        isActive = currentMode == "ocr",
                        onClick = { currentMode = "ocr" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeChip(
                        text = "Detect Color",
                        isActive = currentMode == "color",
                        onClick = { currentMode = "color" },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Second row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeChip(
                        text = "Describe",
                        isActive = currentMode == "describe",
                        onClick = { currentMode = "describe" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeChip(
                        text = "Path & Hazards",
                        isActive = currentMode == "path",
                        onClick = { currentMode = "path" },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Third row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModeChip(
                        text = "Tag Place",
                        isActive = currentMode == "tag",
                        onClick = { currentMode = "tag" },
                        modifier = Modifier.weight(1f)
                    )
                    ModeChip(
                        text = "Recognize",
                        isActive = currentMode == "recognize",
                        onClick = { currentMode = "recognize" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Richer descriptions toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Richer descriptions (online)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = PureBlack
                )
                Switch(
                    checked = useRicherDescriptions,
                    onCheckedChange = { useRicherDescriptions = it },
                    modifier = Modifier.semantics {
                        contentDescription = if (useRicherDescriptions) 
                            "Richer descriptions enabled. Double tap to disable." 
                        else 
                            "Richer descriptions disabled. Double tap to enable."
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureWhite,
                        checkedTrackColor = PureBlack,
                        uncheckedThumbColor = NeutralGray,
                        uncheckedTrackColor = LightGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result banner with fade animation
            AnimatedVisibility(
                visible = showResultBanner && resultText.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ResultBanner(
                    text = resultText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            // Primary action button
            ActionButton(
                text = when (currentMode) {
                    "ocr" -> "Read Text Aloud"
                    "color" -> "Announce Color"
                    "describe" -> "Describe Surroundings"
                    "path" -> "Scan Path & Hazards"
                    "tag" -> "Tag This Place"
                    "recognize" -> "Recognize Place"
                    else -> "Read Text Aloud"
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
                            scope.launch {
                                try {
                                    val result = landmarkRecognizer.recognizeLandmark(frame)
                                    when (result) {
                                        is LandmarkRecognizer.RecognitionResult.Success -> {
                                            resultText = result.message
                                            showResultBanner = true
                                            ttsManager.speak(result.message)
                                            // Auto-hide banner after 6 seconds
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        }
                                        is LandmarkRecognizer.RecognitionResult.NoMatch -> {
                                            resultText = "No saved place recognized"
                                            showResultBanner = true
                                            ttsManager.speak(result.message)
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        }
                                        is LandmarkRecognizer.RecognitionResult.Error -> {
                                            resultText = "Error recognizing place"
                                            showResultBanner = true
                                            ttsManager.speak("Error recognizing place")
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    resultText = "Error recognizing place"
                                    showResultBanner = true
                                    ttsManager.speak("Error recognizing place")
                                    launch {
                                        delay(6000)
                                        showResultBanner = false
                                    }
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
                                    scope.launch {
                                        try {
                                            // Add 700ms settle delay before capture for better stability
                                            delay(700)
                                            
                                            val text = ocrManager.recognizeText(frame, latestRotation)
                                            if (text != null && text.isNotBlank()) {
                                                resultText = text
                                                showResultBanner = true
                                                ttsManager.speak(text)
                                                launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            } else {
                                                resultText = "No clear text detected"
                                                showResultBanner = true
                                                ttsManager.speak("No clear text detected. Move closer and hold steady.")
                                                launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error reading text"
                                            showResultBanner = true
                                            ttsManager.speak("Error reading text")
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                                "color" -> {
                                    // Color detection mode
                                    scope.launch {
                                        try {
                                            val colorName = colorDetector.detectColorAtCenter(frame)
                                            if (colorName == "unknown") {
                                                resultText = "Unable to detect color"
                                                showResultBanner = true
                                                ttsManager.speak("Unable to detect color")
                                                launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            } else {
                                                resultText = "Color detected is $colorName"
                                                showResultBanner = true
                                                ttsManager.speak("Color detected is $colorName")
                                                launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error detecting color"
                                            showResultBanner = true
                                            ttsManager.speak("Error detecting color")
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                                "describe" -> {
                                    // Object detection mode
                                    scope.launch {
                                        try {
                                            val objects = objectDetector.detectObjects(frame, latestRotation)
                                            
                                            // Check if no objects were detected (filtered out or model failed)
                                            if (objects.isEmpty()) {
                                                val description = sceneDescriptionBuilder.buildDescription(objects, frame.width, frame)
                                                resultText = description
                                                showResultBanner = true
                                                ttsManager.speak(description)
                                                launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            } else {
                                                // Use Gemini if toggle is ON and network is available
                                                if (useRicherDescriptions && networkStatusTracker.isCurrentlyOnline()) {
                                                    try {
                                                        // Build object list for Gemini
                                                        val objectList = objects.joinToString(", ") { "${it.label} (confidence: ${it.confidence})" }
                                                        val prompt = "Describe this scene for a blind person. Detected objects: $objectList. Give a brief, helpful description in 1-2 sentences."
                                                        
                                                        // 5 second timeout for Gemini
                                                        val geminiResult = withTimeoutOrNull(5000) {
                                                            geminiClient.queryGemini(prompt)
                                                        }
                                                        
                                                        if (geminiResult != null && geminiResult.isSuccess) {
                                                            val richDescription = geminiResult.getOrNull() ?: ""
                                                            if (richDescription.isNotBlank()) {
                                                                resultText = richDescription
                                                                showResultBanner = true
                                                                ttsManager.speak(richDescription)
                                                                launch {
                                                                    delay(6000)
                                                                    showResultBanner = false
                                                                }
                                                            } else {
                                                                // Fallback to rule-based
                                                                val description = sceneDescriptionBuilder.buildDescription(objects, frame.width, frame)
                                                                resultText = description
                                                                showResultBanner = true
                                                                ttsManager.speak(description)
                                                                launch {
                                                                    delay(6000)
                                                                    showResultBanner = false
                                                                }
                                                            }
                                                        } else {
                                                            // Fallback to rule-based on timeout or error
                                                            val description = sceneDescriptionBuilder.buildDescription(objects, frame.width, frame)
                                                            resultText = description
                                                            showResultBanner = true
                                                            ttsManager.speak(description)
                                                            launch {
                                                                delay(6000)
                                                                showResultBanner = false
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        // Fallback to rule-based on any error
                                                        val description = sceneDescriptionBuilder.buildDescription(objects, frame.width, frame)
                                                        resultText = description
                                                        showResultBanner = true
                                                        ttsManager.speak(description)
                                                        launch {
                                                            delay(6000)
                                                            showResultBanner = false
                                                        }
                                                    }
                                                } else {
                                                    // Use rule-based description
                                                    val description = sceneDescriptionBuilder.buildDescription(objects, frame.width, frame)
                                                    resultText = description
                                                    showResultBanner = true
                                                    ttsManager.speak(description)
                                                    launch {
                                                        delay(6000)
                                                        showResultBanner = false
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error detecting objects"
                                            showResultBanner = true
                                            ttsManager.speak("Error detecting objects")
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                                "path" -> {
                                    // Path & hazards detection mode
                                    scope.launch {
                                        try {
                                            val features = pathFeatureDetector.detectPathFeatures(frame)
                                            val description = pathFeatureDescriptionBuilder.buildDescription(features)
                                            resultText = description
                                            showResultBanner = true
                                            ttsManager.speak(description)
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
                                        } catch (e: Exception) {
                                            resultText = "Error detecting path features"
                                            showResultBanner = true
                                            ttsManager.speak("Error detecting path features")
                                            launch {
                                                delay(6000)
                                                showResultBanner = false
                                            }
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

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary button - Start Navigation
            Button(
                onClick = { showNavigationOverlay = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics {
                        contentDescription = "Start navigation. Double tap to open turn-by-turn directions."
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersonaBlind,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, TextPrimary)
            ) {
                Text(
                    text = "Start Navigation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
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
                                scope.launch {
                                    try {
                                        // Immediate frame capture for better UX
                                        landmarkTagger.tagLocation(
                                            name = landmarkName,
                                            photo = frame,
                                            onSuccess = { tagId ->
                                                resultText = "Place tagged: $landmarkName"
                                                showResultBanner = true
                                                ttsManager.speak("Place tagged: $landmarkName")
                                                landmarkName = ""
                                                showTaggingDialog = false
                                                scope.launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            },
                                            onError = { error ->
                                                resultText = "Failed to tag place"
                                                showResultBanner = true
                                                ttsManager.speak("Failed to tag place")
                                                scope.launch {
                                                    delay(6000)
                                                    showResultBanner = false
                                                }
                                            }
                                        )
                                    } catch (e: Exception) {
                                        resultText = "Failed to tag place"
                                        showResultBanner = true
                                        ttsManager.speak("Failed to tag place")
                                        scope.launch {
                                            delay(6000)
                                            showResultBanner = false
                                        }
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

            // Navigation overlay
            if (showNavigationOverlay) {
                NavigationOverlay(
                    route = currentRoute,
                    onExit = {
                        showNavigationOverlay = false
                    }
                )
            }
        }
    }


/**
 * Mode chip with TalkBack support.
 * Always-visible text: inactive = PureBlack on SoftWhite; active = PureWhite on PureBlack.
 */
@Composable
private fun ModeChip(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isActive) PureBlack else SoftWhite
    val textColor = if (isActive) PureWhite else PureBlack
    val borderColor = if (isActive) PureBlack else SubtleDivider

    Box(
        modifier = modifier
            .height(64.dp)
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isActive) "$text mode selected" else "$text mode. Double tap to select."
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = textColor,          // ALWAYS explicit — never inherited
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
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
                contentDescription = if (isProcessing) "Processing. Please wait." else text
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = PureBlack,
            contentColor = PureWhite
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = PureWhite,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = PureWhite
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
            containerColor = SoftWhite
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SubtleDivider)
    ) {
        Text(
            text = text,
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Result: $text"
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            color = PureBlack,
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
        containerColor = PureWhite,
        title = {
            Text(
                text = "Tag This Place",
                style = MaterialTheme.typography.titleLarge,
                color = PureBlack
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Say or type the name of this place",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SoftBlack
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
                            containerColor = if (isListening) PureBlack else SoftWhite,
                            contentColor = if (isListening) PureWhite else PureBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isListening) PureBlack else SubtleDivider)
                    ) {
                        Text(if (isListening) "Stop Listening" else "Speak")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = landmarkName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureBlack,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tag", color = PureWhite)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = PureBlack)
            }
        }
    )
}