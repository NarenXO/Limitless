package com.teamdexters.limitless.ui.deaf

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.zIndex

val PersonaDeaf = Color(0xFFF791A9)
val LimitlessBackground = Color(0xFFF7F1EE)
val SurfaceTint = Color(0xFFE0F2F4)
val HighlightBox = Color(0xFFFFDBDF)
val TextPrimary = Color(0xFF1F1F1F)

@Composable
fun DeafHomeScreen(
    onBack: () -> Unit = {},
    viewModel: DeafHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isCapturing by viewModel.isCapturing.collectAsState()
    val captions by viewModel.captions.collectAsState()
    val partialCaption by viewModel.partialCaption.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val detectedSound by viewModel.detectedSound.collectAsState()
    
    var isExtraLargeText by remember { mutableStateOf(false) }
    val fontSize = if (isExtraLargeText) 28.sp else 22.sp
    
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startCaptions()
        }
    }
    
    val listState = rememberLazyListState()
    LaunchedEffect(captions.size, partialCaption) {
        val totalItems = captions.size + if (!partialCaption.isNullOrBlank()) 1 else 0
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopCaptions()
        }
    }

    Scaffold(
        containerColor = LimitlessBackground,
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
                            contentDescription = "Go back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Deaf & Hard-of-Hearing Assist",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { contentDescription = "Deaf and Hard of Hearing Assistant" }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Detected Sound Alert
            if (detectedSound != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .semantics { contentDescription = "Detected sound: $detectedSound" },
                    colors = CardDefaults.cardColors(containerColor = HighlightBox),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "⚠️ Detected: $detectedSound",
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Chip & Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCapturing) Color(0xFFC8E6C9) else HighlightBox, // Greenish for active, Pinkish for stopped
                    modifier = Modifier.semantics { contentDescription = "Status: $statusMessage" }
                ) {
                    Text(
                        text = if (isCapturing) "🟢 $statusMessage" else "🔴 $statusMessage",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.clearCaptions() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceTint, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Captions", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = { isExtraLargeText = !isExtraLargeText },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceTint, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isExtraLargeText) "Text: Normal" else "Text: Large", fontWeight = FontWeight.Bold)
                }
            }
            
            // Live Captions Display Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp)
                    .border(3.dp, PersonaDeaf, RoundedCornerShape(16.dp))
                    .semantics { contentDescription = "Live captions display area" },
                colors = CardDefaults.cardColors(containerColor = SurfaceTint),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (!hasMicPermission) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Microphone permission required",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                colors = ButtonDefaults.buttonColors(containerColor = PersonaDeaf),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(captions) { text ->
                            Text(
                                text = text,
                                color = TextPrimary,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { contentDescription = text }
                            )
                        }
                        if (!partialCaption.isNullOrBlank()) {
                            item {
                                Text(
                                    text = partialCaption!!,
                                    color = TextPrimary.copy(alpha = 0.7f),
                                    fontSize = fontSize,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = "Current partial caption: $partialCaption" }
                                )
                            }
                        }
                    }
                }
            }
            
            // Start / Stop Button
            Button(
                onClick = {
                    if (isCapturing) {
                        viewModel.stopCaptions()
                    } else {
                        if (hasMicPermission) {
                            viewModel.startCaptions()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(bottom = 12.dp)
                    .semantics { 
                        contentDescription = if (isCapturing) "Stop live captions" else "Start live captions" 
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCapturing) HighlightBox else PersonaDeaf,
                    contentColor = if (isCapturing) TextPrimary else Color.White
                ),
                shape = RoundedCornerShape(30.dp)
            ) {
                Text(
                    text = if (isCapturing) "STOP CAPTIONS" else "START LIVE CAPTIONS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
