package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
@Composable
fun BlindHomeScreen(onBack: () -> Unit = {}) {
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

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val query = data?.get(0)?.lowercase() ?: ""
            if (query.isNotEmpty() && !isProcessing) {
                isProcessing = true
                resultText = ""
                
                val frame = latestFrame
                if (frame == null) {
                    ttsManager.speak("Camera not ready")
                    isProcessing = false
                    return@rememberLauncherForActivityResult
                }
                
                scope.launch {
                    try {
                        if (query.contains("read") || query.contains("text") || query.contains("sign") || query.contains("book")) {
                            delay(700)
                            val text = ocrManager.recognizeText(frame, latestRotation)
                            if (!text.isNullOrBlank()) {
                                resultText = "Text reads: $text"
                            } else {
                                resultText = "No readable text found"
                            }
                        } else if (query.contains("describe") || query.contains("surroundings") || query.contains("where") || query.contains("path")) {
                            val features = pathFeatureDetector.detectPathFeatures(frame)
                            val objects = objectDetector.detectObjects(frame, latestRotation)
                            
                            val pathStatus = if (features.isNotEmpty()) "BLOCKED" else "CLEAR"
                            val objectList = objects.joinToString(", ") { it.label }
                            
                            resultText = "Path status: $pathStatus. Obstacles: ${if (features.isNotEmpty()) features.joinToString(", ") { it.label } else "None"}. Objects: ${if (objectList.isNotEmpty()) objectList else "None"}."
                            
                            // Optional Gemini query if network is up
                            val prompt = "Describe this scene. Detected path features: ${features.joinToString(", "){it.label}}, objects: $objectList. Format: Path status: [CLEAR/BLOCKED]. Obstacles: [NAME OBSTACLES]. Objects: [KEY OBJECTS]."
                            val geminiResult = withTimeoutOrNull(5000) { geminiClient.queryGemini(prompt) }
                            if (geminiResult != null && geminiResult.isSuccess) {
                                val desc = geminiResult.getOrNull() ?: ""
                                if (desc.isNotBlank()) resultText = desc
                            }
                        } else if (query.contains("object") || query.contains("see") || query.contains("find") || query.contains("laptop")) {
                            val objects = objectDetector.detectObjects(frame, latestRotation)
                            val objectList = objects.joinToString(", ") { "${it.label} (${(it.confidence*100).toInt()}%)" }
                            resultText = "Objects in front of you: ${if (objectList.isNotEmpty()) objectList else "None detected"}"
                        } else if (query.contains("color")) {
                            val colorName = colorDetector.detectColorAtCenter(frame)
                            resultText = "Color in front of camera is $colorName."
                        } else {
                            resultText = "I can help you read text, describe surroundings, detect objects, or find colors. What would you like to do?"
                        }
                        
                        ttsManager.stop()
                        try {
                            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                vibrator.vibrate(200)
                            }
                        } catch(e: Exception) {}
                        ttsManager.speak(resultText)
                        
                        launch {
                            delay(6000)
                            resultText = ""
                        }
                    } catch (e: Exception) {
                        resultText = "Error processing request"
                        ttsManager.speak(resultText)
                        launch { delay(6000); resultText = "" }
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val query = data?.get(0)?.lowercase() ?: ""
            if (query.isNotEmpty() && !isProcessing) {
                isProcessing = true
                resultText = ""
                
                val frame = latestFrame
                if (frame == null) {
                    ttsManager.speak("Camera not ready")
                    isProcessing = false
                    return@rememberLauncherForActivityResult
                }
                
                scope.launch {
                    try {
                        if (query.contains("read") || query.contains("text") || query.contains("sign") || query.contains("book")) {
                            delay(700)
                            val text = ocrManager.recognizeText(frame, latestRotation)
                            if (!text.isNullOrBlank()) {
                                resultText = "Text reads: $text"
                            } else {
                                resultText = "No readable text found. Please hold the document steady directly in front of the camera."
                            }
                        } else if (query.contains("describe") || query.contains("surroundings") || query.contains("where") || query.contains("path")) {
                            val features = pathFeatureDetector.detectPathFeatures(frame)
                            val objects = objectDetector.detectObjects(frame, latestRotation)
                            
                            val pathStatus = if (features.isNotEmpty()) "BLOCKED" else "CLEAR"
                            val objectList = objects.joinToString(", ") { it.label }
                            
                            // Optional Gemini query if network is up
                            val prompt = "You are a high-accuracy mobility guide for a blind person. Analyze this photo in detail.\nProvide a clear, categorized 3-part spatial report:\n1. PATH CLEARANCE: State exact path safety (e.g. \"Path is clear for 2 meters straight ahead\" or \"Path is blocked by a table 1 meter ahead\").\n2. NEAREST OBSTACLES: List hazardous obstacles on left, right, and center with estimated distances.\n3. CATEGORIZED OBJECTS: List visible furniture, doors, ramps, and devices."
                            val geminiResult = withTimeoutOrNull(5000) { geminiClient.queryGemini(prompt) }
                            if (geminiResult != null && geminiResult.isSuccess) {
                                val desc = geminiResult.getOrNull() ?: ""
                                if (desc.isNotBlank()) {
                                    resultText = desc
                                } else {
                                    resultText = "Path status: $pathStatus. Obstacles: ${if (features.isNotEmpty()) features.joinToString(", ") { it.label } else "None"}. Objects: ${if (objectList.isNotEmpty()) objectList else "None"}."
                                }
                            } else {
                                resultText = "Path status: $pathStatus. Obstacles: ${if (features.isNotEmpty()) features.joinToString(", ") { it.label } else "None"}. Objects: ${if (objectList.isNotEmpty()) objectList else "None"}."
                            }
                        } else if (query.contains("object") || query.contains("see") || query.contains("find") || query.contains("laptop")) {
                            val objects = objectDetector.detectObjects(frame, latestRotation)
                            val objectList = objects.joinToString(", ") { "${it.label} (${(it.confidence*100).toInt()}%)" }
                            resultText = "Objects detected: ${if (objectList.isNotEmpty()) objectList else "None detected"}"
                        } else if (query.contains("color")) {
                            val colorName = colorDetector.detectColorAtCenter(frame)
                            resultText = "Color in front of camera is $colorName."
                        } else {
                            resultText = "I can help you read text, describe surroundings, detect objects, or find colors. What would you like to do?"
                        }
                        
                        ttsManager.stop()
                        try {
                            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                            } else {
                                vibrator.vibrate(200)
                            }
                        } catch(e: Exception) {}
                        ttsManager.speak(resultText)
                        
                        launch {
                            delay(8000)
                            resultText = ""
                        }
                    } catch (e: Exception) {
                        resultText = "Error processing request"
                        ttsManager.speak(resultText)
                        launch { delay(8000); resultText = "" }
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        ttsManager.speak("Blind Assist active. What would you like me to do? Say: Describe surroundings, Read text, Detect objects, or Find color.") {
            try {
                val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(150)
                }
            } catch(e: Exception) {}
            
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                }
                speechRecognizerLauncher.launch(intent)
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(100f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onBack() },
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
                        text = "Blind & Low-Vision",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }
        },
        containerColor = LimitlessBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LimitlessBackground)
        ) {
            if (cameraPermission) {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    showReticle = false,
                    onFrameReady = { bitmap -> latestFrame = bitmap },
                    onCaptureReady = { bitmap, rotation ->
                        latestFrame = bitmap
                        latestRotation = rotation
                    },
                    onError = { }
                )
            } else {
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
            
            // CENTER OF THE PAGE — BIG HIGH-CONTRAST RESPONSE CARD
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(260.dp)
                        .border(3.dp, PersonaBlind, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = SurfaceTint),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isProcessing) {
                            com.teamdexters.limitless.ui.components.SafeCircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = PersonaBlind,
                                strokeWidth = 4.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Hazel is analyzing scene in detail...",
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        } else if (resultText.isNotEmpty()) {
                            Text(
                                text = resultText,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "🎙️ Listening...\n\nSpeak any query:\n• 'Describe surroundings'\n• 'Read text'\n• 'Detect objects'\n• 'Find color'",
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // BOTTOM SECTION — SINGLE VOICE ASSISTANT MIC FAB
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { 
                            ttsManager.stop()
                            resultText = ""
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            }
                            speechRecognizerLauncher.launch(intent)
                        },
                        containerColor = PersonaBlind,
                        modifier = Modifier.size(72.dp),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {
                        Text("🎙️", fontSize = 32.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "🎙️ Tap to Speak to Hazel",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
