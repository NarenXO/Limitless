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
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AccessibilityScannerScreen(
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
        containerColor = Color(0xFFF7F1EE)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Camera Preview Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isScanning) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFF791A9))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scanning...", color = Color.White)
                        }
                    }
                } else {
                    // Capture Button
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(64.dp)
                            .background(Color(0xFFF791A9), CircleShape)
                            .clickable {
                                // Mock generating a Bitmap to simulate capture
                                val mockBitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
                                viewModel.startScan(mockBitmap)
                            }
                            .semantics { contentDescription = "Capture Frame for Analysis" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                    }
                }
            }

            // Results Card
            if (scanObjects.isNotEmpty() || signageText.isNotEmpty() || lightingScore > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .semantics { contentDescription = "Scan Results Card" },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F4))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Analysis Results", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F1F1F))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Detected Objects:", style = MaterialTheme.typography.bodyMedium)
                        scanObjects.forEach { obj ->
                            Text("- ${obj.label} (${(obj.confidence * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Signage Keywords:", style = MaterialTheme.typography.bodyMedium)
                        if (signageText.isEmpty()) Text("- None found", style = MaterialTheme.typography.bodySmall)
                        else Text("- ${signageText.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Lighting Score:", style = MaterialTheme.typography.bodyMedium)
                        val lightStatus = if (lightingScore > 60) "Good Lighting" else "Dim Lighting"
                        Text("- $lightingScore/100 - $lightStatus", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Manual Checklist
            Column(modifier = Modifier.padding(16.dp)) {
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
                    onClick = { viewModel.saveScan() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics { contentDescription = "Save Scan Results" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9), contentColor = Color.White)
                ) {
                    Text("Save Scan")
                }
            }
        }
    }
}
