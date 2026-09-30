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
    var isARViewEnabled by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(saveResult) {
        if (saveResult == true) {
            snackbarHostState.showSnackbar("Scan saved successfully!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF7F1EE)
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
            val context = androidx.compose.ui.platform.LocalContext.current
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
                                    var lastScanTime = 0L
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
                                        android.util.Log.d("LIMITLESS_TRACE", "Scanner camera preview bound to lifecycle")
                                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
                                    } catch (exc: Exception) {
                                        // Handle errors
                                    }
                                }, executor)
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        BoundingBoxOverlay(
                            detections = liveDetections,
                            imageWidth = 640, // We would normally use preview size, but this works if scaled inside Canvas
                            imageHeight = 480,
                            modifier = Modifier.fillMaxSize().semantics { contentDescription = "Live object tracking overlay" }
                        )

                        if (currentDoorWidth != null && !isARViewEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .background(Color(0xFFE0F2F4), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .semantics { 
                                        contentDescription = "Doorway detected: ${currentDoorWidth?.label} (${currentDoorWidth?.estimatedCm})"
                                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                                    }
                            ) {
                                Text(
                                    text = "Doorway: ${currentDoorWidth?.label} (${currentDoorWidth?.estimatedCm})",
                                    color = Color(0xFF1F1F1F),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (isARViewEnabled) {
                            ThreeDPathOverlay(
                                pathClarity = comprehensiveScore?.pathClarity,
                                modifier = Modifier.fillMaxSize()
                            )
                            ThreeDMiniMapWidget(
                                pathClarity = comprehensiveScore?.pathClarity,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            )
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

                // AR Toggle & Capture Button Row
                if (isCameraGranted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { isARViewEnabled = !isARViewEnabled },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isARViewEnabled) Color(0xFFF791A9) else Color(0xFFE0F2F4),
                                contentColor = if (isARViewEnabled) Color.White else TextPrimary
                            ),
                            shape = CircleShape
                        ) {
                            Text(if (isARViewEnabled) "🌐 AR ON" else "🌐 3D AR View", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        
                        Button(
                            onClick = { viewModel.captureAndScan() },
                            modifier = Modifier.weight(2f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F)),
                            shape = CircleShape
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture & Scan", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    }
                }

                // Live Detection Chips
                if (scanObjects.isNotEmpty() || signageText.isNotEmpty() || lightingScore > 0) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (scanObjects.isNotEmpty()) {
                            item {
                                val detectedLabels = scanObjects.map { it.label.lowercase().replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() } }.distinct().joinToString(", ")
                                androidx.compose.material3.SuggestionChip(
                                    onClick = {},
                                    label = { Text("Objects: $detectedLabels", color = TextPrimary) },
                                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = HighlightBox)
                                )
                            }
                        }
                        if (signageText.isNotEmpty()) {
                            item {
                                androidx.compose.material3.SuggestionChip(
                                    onClick = {},
                                    label = { Text("Text Detected", color = TextPrimary) },
                                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = HighlightBox)
                                )
                            }
                        }
                        item {
                            val lightStatus = if (lightingScore > 60) "Lighting: Good" else "Lighting: Dim"
                            androidx.compose.material3.SuggestionChip(
                                onClick = {},
                                label = { Text(lightStatus, color = TextPrimary) },
                                colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = HighlightBox)
                            )
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
