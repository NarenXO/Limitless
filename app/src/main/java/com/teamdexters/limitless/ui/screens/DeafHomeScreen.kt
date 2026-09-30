package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamdexters.limitless.feature.deaf.caption.CaptionEngineStatus
import com.teamdexters.limitless.feature.deaf.caption.CaptionViewModel
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Deaf & Hard of Hearing Assistant - Live Captions Screen
 * Provides real-time offline speech-to-text captions using Vosk.
 */
@Composable
fun DeafHomeScreen(
    viewModel: CaptionViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var hasMicPermission by remember {
        mutableStateOf(checkMicrophonePermission(context))
    }
    
    // Auto-scroll to latest caption
    LaunchedEffect(uiState.captionLines.size, uiState.partialText) {
        if (uiState.captionLines.isNotEmpty() || uiState.partialText.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(
                    index = uiState.captionLines.size + if (uiState.partialText.isNotEmpty()) 1 else 0
                )
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
            .padding(16.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Offline capable status chip
            Box(
                modifier = Modifier
                    .background(SurfaceTint, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Offline capable",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            
            // Title
            Text(
                text = "Live Captions",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.width(80.dp)) // Balance the layout
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Warning Chips
        when (uiState.status) {
            CaptionEngineStatus.MODEL_MISSING -> {
                Box(
                    modifier = Modifier
                        .background(HighlightBox, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Caption model not loaded",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            CaptionEngineStatus.MIC_DENIED -> {
                Box(
                    modifier = Modifier
                        .background(HighlightBox, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Microphone permission required",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            else -> {}
        }
        
        // Start/Stop Button
        Button(
            onClick = {
                if (uiState.status == CaptionEngineStatus.LISTENING) {
                    viewModel.stopListening()
                } else {
                    if (!hasMicPermission) {
                        hasMicPermission = checkMicrophonePermission(context)
                    }
                    viewModel.startListening()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PersonaDeaf
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (uiState.status == CaptionEngineStatus.LISTENING) "Stop" else "Start",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Live Caption Container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceTint, RoundedCornerShape(16.dp))
                .border(1.dp, PersonaDeaf, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.captionLines) { line ->
                    CaptionLineItem(line = line)
                }
                
                // Partial text (in-progress)
                if (uiState.partialText.isNotEmpty()) {
                    item {
                        Text(
                            text = uiState.partialText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.alpha(0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual caption line item.
 */
@Composable
private fun CaptionLineItem(line: com.teamdexters.limitless.feature.deaf.caption.CaptionLine) {
    Text(
        text = line.text,
        fontSize = 24.sp,
        fontWeight = if (line.isFinal) FontWeight.Bold else FontWeight.Medium,
        color = TextPrimary
    )
}

/**
 * Check if microphone permission is granted.
 */
private fun checkMicrophonePermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}