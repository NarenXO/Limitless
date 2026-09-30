package com.teamdexters.limitless.ui.scanner

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class, com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun AccessibilityScannerScreen(
    navController: androidx.navigation.NavHostController,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val liveDetections by viewModel.liveDetections.collectAsState()
    val currentDoorWidth by viewModel.currentDoorWidth.collectAsState()
    val categorizedResult by viewModel.categorizedResult.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var isCaptured by remember { mutableStateOf(false) }

    // TTS Setup
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        lateinit var ttsObj: android.speech.tts.TextToSpeech
        ttsObj = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                ttsObj.language = java.util.Locale.US
                ttsReady = true
            }
        }
        ttsObj
    }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    LaunchedEffect(categorizedResult) {
        categorizedResult?.let {
            isCaptured = true
            if (ttsReady) {
                tts.speak(it.spokenVoiceSummary, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "SCAN_VOICE")
            }
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }
    }

    var assistantReply by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF7F1EE),
        floatingActionButton = {
            if (isCaptured) {
                FloatingActionButton(
                    onClick = { 
                        // Mock Assistant Reply
                        val reply = "I can see the scene. Let me know what you are looking for."
                        assistantReply = reply
                        if (ttsReady) tts.speak(reply, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                    },
                    containerColor = Color(0xFFF791A9),
                    contentColor = Color.White
                ) {
                    Icon(imageVector = androidx.compose.material.icons.Icons.Default.Mic, contentDescription = "Ask Scan Assistant")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F1EE))
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val cameraPermissionState = com.google.accompanist.permissions.rememberPermissionState(android.Manifest.permission.CAMERA)
            val isCameraGranted = cameraPermissionState.status == com.google.accompanist.permissions.PermissionStatus.Granted

            if (!isCaptured) {
                // Live CameraX Preview
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCameraGranted) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    val previewView = androidx.camera.view.PreviewView(ctx)
                                    val executor = androidx.core.content.ContextCompat.getMainExecutor(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = androidx.camera.core.Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        val analysis = androidx.camera.core.ImageAnalysis.Builder()
                                            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            
                                        var lastLiveTime = 0L
                                        analysis.setAnalyzer(executor) { imageProxy ->
                                            try {
                                                val currentTime = System.currentTimeMillis()
                                                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null) {
                                                    val bitmap = imageProxy.toBitmap()
                                                    if (currentTime - lastLiveTime >= 500) {
                                                        lastLiveTime = currentTime
                                                        viewModel.processLiveFrame(bitmap)
                                                    }
                                                    viewModel.updateLatestFrame(bitmap, imageProxy.imageInfo.rotationDegrees)
                                                    com.teamdexters.limitless.assistant.vision.CameraFrameManager.updateFrame(bitmap)
                                                }
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                        
                                        val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
                                        } catch (exc: Exception) {
                                        }
                                    }, executor)
                                    previewView
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            BoundingBoxOverlay(
                                detections = liveDetections,
                                imageWidth = 640,
                                imageHeight = 480,
                                modifier = Modifier.fillMaxSize().semantics { contentDescription = "Live object tracking overlay" }
                            )

                            if (currentDoorWidth != null) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(Color(0xFFE0F2F4), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                    ) {
                                    Text(
                                        text = "Doorway: ${currentDoorWidth?.label}",
                                        color = Color(0xFF1F1F1F),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = HighlightBox),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.CameraAlt,
                                        contentDescription = "Camera Permission Required",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Camera Permission Required",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Limitless needs camera access to detect ramps, stairs, doorways, and read signage.",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { cameraPermissionState.launchPermissionRequest() },
                                        colors = ButtonDefaults.buttonColors(containerColor = TextPrimary, contentColor = HighlightBox)
                                    ) {
                                        Text("Grant Camera Permission")
                                    }
                                }
                            }
                        }
                    }

                    if (isCameraGranted) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.captureAndScan() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                            shape = CircleShape
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            if (isScanning) {
                                Text("Scanning...", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            } else {
                                Text("📸 Capture & Scan Scene", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // BIG RESULT CONTAINER
                categorizedResult?.let { result ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE0F2F4), RoundedCornerShape(16.dp))
                            .border(3.dp, Color(0xFFF791A9), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            // Top Image Preview
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Gray),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Main Synthesized Sentence with Emojis
                            val objectsText = if (result.detectedObjects.isEmpty()) "None" else result.detectedObjects.joinToString(", ")
                            val emotionText = result.personEmotion ?: "No Person Detected 👤"
                            val lightingText = result.lightingScoreText
                            
                            val synthesizedText = "📦 Objects Found: $objectsText\n$emotionText\n💡 Lighting: $lightingText"
                            Text(
                                text = synthesizedText,
                                color = Color(0xFF1F1F1F),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = androidx.compose.ui.unit.sp(20)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Detailed Categorized Rows
                            Text("📦 Detected Objects", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = androidx.compose.ui.unit.sp(16))
                            Text(objectsText, color = Color(0xFF1F1F1F), fontSize = androidx.compose.ui.unit.sp(16))
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("👤 Person & Emotion", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = androidx.compose.ui.unit.sp(16))
                            Text(emotionText, color = Color(0xFF1F1F1F), fontSize = androidx.compose.ui.unit.sp(16))
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text("💡 Lighting Condition", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = androidx.compose.ui.unit.sp(16))
                            Text(lightingText, color = Color(0xFF1F1F1F), fontSize = androidx.compose.ui.unit.sp(16))
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Action Buttons
                            Button(
                                onClick = { 
                                    isCaptured = false
                                    viewModel.resetScan() 
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9)),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("🔄 Retake / Scan New Scene", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                onClick = { 
                                    if (ttsReady) {
                                        tts.speak(result.spokenVoiceSummary, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDBDF)),
                                shape = RoundedCornerShape(28.dp)
                            ) {
                                Text("🔊 Speak Report Again", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // Assistant Reply Banner
                assistantReply?.let { reply ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDBDF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Assistant: $reply",
                            color = Color(0xFF1F1F1F),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
