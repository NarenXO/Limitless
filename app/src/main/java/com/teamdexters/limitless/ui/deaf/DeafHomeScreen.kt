package com.teamdexters.limitless.ui.deaf

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel

val LimitlessBackground = Color(0xFFF7F1EE)
val PersonaDeaf = Color(0xFFF791A9)
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
    val partial by viewModel.partial.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val fontSizeSp by viewModel.fontSizeSp.collectAsState()
    
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
    
    val scrollState = rememberScrollState()
    LaunchedEffect(captions, partial) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopCaptions()
        }
    }

    Scaffold(
        containerColor = LimitlessBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Text(
                    text = "Deaf & Hard-of-Hearing Assist",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = "Deaf and Hard of Hearing Assistant" }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Status Chip
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isCapturing) Color(0xFFC8E6C9) else HighlightBox,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .semantics { contentDescription = "Status: $statusMessage" }
            ) {
                Text(
                    text = statusMessage,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
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

            // Live Captions Display Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 12.dp)
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Microphone permission required to show live captions",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                    ) {
                        if (captions.isEmpty() && partial.isEmpty()) {
                            Text(
                                text = "Tap START LIVE CAPTIONS. Speak near the phone. Text will appear here in large type.",
                                color = TextPrimary.copy(alpha = 0.6f),
                                fontSize = fontSizeSp.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "$captions $partial".trim(),
                                color = TextPrimary,
                                fontSize = fontSizeSp.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.clearCaptions() },
                    modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "Clear Captions" },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceTint, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Captions", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = { viewModel.toggleFontSize() },
                    modifier = Modifier.weight(1f).height(56.dp).semantics { contentDescription = "Toggle text size" },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceTint, contentColor = TextPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (fontSizeSp == 24) "Text Size: Large" else "Text Size: Extra Large", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
