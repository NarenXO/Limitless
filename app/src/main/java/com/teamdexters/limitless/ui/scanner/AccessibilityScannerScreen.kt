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
    val scanObjects by viewModel.scanObjects.collectAsState()
    val signageText by viewModel.signageText.collectAsState()
    val lightingScore by viewModel.lightingScore.collectAsState()
    val doorWidth by viewModel.doorWidth.collectAsState()
    val hasBraille by viewModel.hasBraille.collectAsState()
    val hasWashroom by viewModel.hasWashroom.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    
    val liveDetections by viewModel.liveDetections.collectAsState()
    val currentDoorWidth by viewModel.currentDoorWidth.collectAsState()
    val comprehensiveScore by viewModel.comprehensiveScore.collectAsState()
    val categorizedResult by viewModel.categorizedResult.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(saveResult) {
        if (saveResult == true) {
            snackbarHostState.showSnackbar("Scan saved successfully!")
        }
    }
    
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
            if (ttsReady) {
                tts.speak(it.spokenVoiceSummary, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    var assistantReply by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF7F1EE),
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (categorizedResult != null) {
                    FloatingActionButton(
                        onClick = {
                            if (ttsReady) {
                                tts.speak(categorizedResult!!.spokenVoiceSummary, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        },
                        containerColor = Color(0xFF1F1F1F),
                        contentColor = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(imageVector = androidx.compose.material.icons.Icons.Default.VolumeUp, contentDescription = "Speak Scan Report Again")
                    }
                }
                FloatingActionButton(
                    onClick = { 
                        // Mock Assistant Reply
                        val reply = "Based on the scan, yes, the path is clear."
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
            // Live CameraX Preview
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }
            val cameraPermissionState = com.google.accompanist.permissions.rememberPermissionState(android.Manifest.permission.CAMERA)
            val isCameraGranted = cameraPermissionState.status == com.google.accompanist.permissions.PermissionStatus.Granted

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
                        // Camera Permission Request UI
                        Card(modifier = Modifier.fillMaxWidth()) {}
                    }
                }

                if (isCameraGranted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.captureAndScan() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                        shape = CircleShape
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isScanning) {
                            Text("Scanning...", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        } else {
                            Text("Capture & Scan", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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

                // 3 Category Cards for CategorizedScanResult
                categorizedResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Path Safety
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("PATH SAFETY", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(result.pathSafetyStatus, color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    
                    // Person & Emotion
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("PERSON & EMOTION", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(result.personEmotion ?: "No Person Detected", color = Color(0xFF1F1F1F), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    // Objects Detected
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("DETECTED OBJECTS", color = Color(0xFF1F1F1F), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(if (result.detectedObjects.isEmpty()) "None" else result.detectedObjects.joinToString("\n"), color = Color(0xFF1F1F1F), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                comprehensiveScore?.let { scoreResult ->
                    Spacer(modifier = Modifier.height(16.dp))
                    ScoreBreakdownCard(
                        result = scoreResult,
                        onNavigateRoute = {
                            navController.navigate(com.teamdexters.limitless.ui.navigation.Screen.MobilityHome.route)
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            // Manual Checklist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text("Manual Checklist", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Door Width (Reference: Standard is 80-90cm)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Narrow", "Standard", "Wide").forEach { w ->
                        FilterChip(
                            selected = doorWidth == w,
                            onClick = { viewModel.onDoorWidthSelected(w) },
                            label = { Text(w) },
                            modifier = Modifier.semantics { contentDescription = "Select Door Width $w" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Braille Signage Present", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hasBraille,
                        onClick = { viewModel.onBrailleSelected(true) },
                        label = { Text("Yes") },
                        modifier = Modifier.semantics { contentDescription = "Braille Signage Yes" }
                    )
                    FilterChip(
                        selected = !hasBraille,
                        onClick = { viewModel.onBrailleSelected(false) },
                        label = { Text("No") },
                        modifier = Modifier.semantics { contentDescription = "Braille Signage No" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Accessible Washroom", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hasWashroom,
                        onClick = { viewModel.onWashroomSelected(true) },
                        label = { Text("Yes") },
                        modifier = Modifier.semantics { contentDescription = "Accessible Washroom Yes" }
                    )
                    FilterChip(
                        selected = !hasWashroom,
                        onClick = { viewModel.onWashroomSelected(false) },
                        label = { Text("No") },
                        modifier = Modifier.semantics { contentDescription = "Accessible Washroom No" }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { 
                        android.util.Log.d("LIMITLESS_TRACE", "Scanner score calculation triggered and saved")
                        viewModel.saveScan { insertedId ->
                            navController.navigate(com.teamdexters.limitless.ui.navigation.Screen.LocationDetail.createRoute(insertedId))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9))
                ) {
                    Text("GENERATE SCORE", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}
