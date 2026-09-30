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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import com.teamdexters.limitless.ui.theme.*

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
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(saveResult) {
        if (saveResult == true) {
            snackbarHostState.showSnackbar("Scan saved successfully!")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = PureWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live CameraX Preview
            val context = androidx.compose.ui.platform.LocalContext.current
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }
            val cameraPermissionState = com.google.accompanist.permissions.rememberPermissionState(android.Manifest.permission.CAMERA)
            val isCameraGranted = cameraPermissionState.status == com.google.accompanist.permissions.PermissionStatus.Granted

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                                        
                                    var lastAnalyzeTime = 0L
                                    analysis.setAnalyzer(executor) { imageProxy ->
                                        try {
                                            val currentTime = System.currentTimeMillis()
                                            if (!isScanning && (currentTime - lastAnalyzeTime >= 1500)) {
                                                lastAnalyzeTime = currentTime
                                                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                                                val mediaImage = imageProxy.image
                                                if (mediaImage != null) {
                                                    val bitmap = imageProxy.toBitmap()
                                                    com.teamdexters.limitless.assistant.vision.CameraFrameManager.updateFrame(bitmap)
                                                    viewModel.startScan(bitmap, imageProxy.imageInfo.rotationDegrees)
                                                }
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
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.CameraAlt,
                                    contentDescription = "Camera Permission Required",
                                    tint = PureBlack,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Camera permission required to scan building accessibility",
                                    color = PureBlack,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { cameraPermissionState.launchPermissionRequest() },
                                    colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Grant Camera Permission", color = PureWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
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
                                androidx.compose.material3.SuggestionChip(
                                    onClick = {},
                                    label = { Text("Objects Detected", color = PureBlack) },
                                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = SoftWhite),
                                    border = androidx.compose.material3.SuggestionChipDefaults.suggestionChipBorder(borderColor = SubtleDivider)
                                )
                            }
                        }
                        if (signageText.isNotEmpty()) {
                            item {
                                androidx.compose.material3.SuggestionChip(
                                    onClick = {},
                                    label = { Text("Text Detected", color = PureBlack) },
                                    colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = SoftWhite),
                                    border = androidx.compose.material3.SuggestionChipDefaults.suggestionChipBorder(borderColor = SubtleDivider)
                                )
                            }
                        }
                        item {
                            val lightStatus = if (lightingScore > 60) "Lighting: Good" else "Lighting: Dim"
                            androidx.compose.material3.SuggestionChip(
                                onClick = {},
                                label = { Text(lightStatus, color = PureBlack) },
                                colors = androidx.compose.material3.SuggestionChipDefaults.suggestionChipColors(containerColor = SoftWhite),
                                border = androidx.compose.material3.SuggestionChipDefaults.suggestionChipBorder(borderColor = SubtleDivider)
                            )
                        }
                    }
                }
            }

            // Manual Checklist
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Manual Checklist",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Door Width (Reference: Standard is 80-90cm)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Narrow", "Standard", "Wide").forEach { w ->
                        FilterChip(
                            selected = doorWidth == w,
                            onClick = { viewModel.onDoorWidthSelected(w) },
                            label = { Text(w) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PureBlack,
                                selectedLabelColor = PureWhite,
                                containerColor = SoftWhite,
                                labelColor = PureBlack
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (doorWidth == w) PureBlack else SubtleDivider,
                                selectedBorderColor = PureBlack
                            ),
                            modifier = Modifier.semantics { contentDescription = "Select Door Width $w" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Braille Signage Present",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hasBraille,
                        onClick = { viewModel.onBrailleSelected(true) },
                        label = { Text("Yes") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PureBlack,
                            selectedLabelColor = PureWhite,
                            containerColor = SoftWhite,
                            labelColor = PureBlack
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (hasBraille) PureBlack else SubtleDivider,
                            selectedBorderColor = PureBlack
                        ),
                        modifier = Modifier.semantics { contentDescription = "Braille Signage Yes" }
                    )
                    FilterChip(
                        selected = !hasBraille,
                        onClick = { viewModel.onBrailleSelected(false) },
                        label = { Text("No") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PureBlack,
                            selectedLabelColor = PureWhite,
                            containerColor = SoftWhite,
                            labelColor = PureBlack
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (!hasBraille) PureBlack else SubtleDivider,
                            selectedBorderColor = PureBlack
                        ),
                        modifier = Modifier.semantics { contentDescription = "Braille Signage No" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Accessible Washroom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureBlack,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = hasWashroom,
                        onClick = { viewModel.onWashroomSelected(true) },
                        label = { Text("Yes") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PureBlack,
                            selectedLabelColor = PureWhite,
                            containerColor = SoftWhite,
                            labelColor = PureBlack
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (hasWashroom) PureBlack else SubtleDivider,
                            selectedBorderColor = PureBlack
                        ),
                        modifier = Modifier.semantics { contentDescription = "Accessible Washroom Yes" }
                    )
                    FilterChip(
                        selected = !hasWashroom,
                        onClick = { viewModel.onWashroomSelected(false) },
                        label = { Text("No") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PureBlack,
                            selectedLabelColor = PureWhite,
                            containerColor = SoftWhite,
                            labelColor = PureBlack
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (!hasWashroom) PureBlack else SubtleDivider,
                            selectedBorderColor = PureBlack
                        ),
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("GENERATE SCORE", color = PureWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }

}
