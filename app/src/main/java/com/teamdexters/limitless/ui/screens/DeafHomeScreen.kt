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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.teamdexters.limitless.feature.deaf.sound.SoundAlertViewModel
import com.teamdexters.limitless.feature.deaf.sound.VibrationVocabulary
import com.teamdexters.limitless.ui.components.SoundAlertBanner
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Deaf & Hard of Hearing Assistant - Live Captions Screen
 * Provides real-time offline speech-to-text captions using Vosk.
 * Integrated with YAMNet sound alerts and vibration vocabulary.
 */
@Composable
fun DeafHomeScreen(
    captionViewModel: CaptionViewModel = viewModel(),
    soundAlertViewModel: SoundAlertViewModel = viewModel()
) {
    val context = LocalContext.current
    val captionUiState by captionViewModel.uiState.collectAsState()
    val soundAlertUiState by soundAlertViewModel.activeAlert.collectAsState()
    val isListening by soundAlertViewModel.isListening.collectAsState()
    val isModelLoaded by soundAlertViewModel.isModelLoaded.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var hasMicPermission by remember {
        mutableStateOf(checkMicrophonePermission(context))
    }
    
    // Auto-scroll to latest caption
    LaunchedEffect(captionUiState.captionLines.size, captionUiState.partialText) {
        if (captionUiState.captionLines.isNotEmpty() || captionUiState.partialText.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(
                    index = captionUiState.captionLines.size + if (captionUiState.partialText.isNotEmpty()) 1 else 0
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
        when (captionUiState.status) {
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
                if (captionUiState.status == CaptionEngineStatus.LISTENING) {
                    captionViewModel.stopListening()
                } else {
                    if (!hasMicPermission) {
                        hasMicPermission = checkMicrophonePermission(context)
                    }
                    captionViewModel.startListening()
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
                text = if (captionUiState.status == CaptionEngineStatus.LISTENING) "Stop" else "Start",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sound Alerts Section
        SoundAlertsSection(
            isListening = isListening,
            isModelLoaded = isModelLoaded,
            onToggleListening = { soundAlertViewModel.toggleListening() },
            onTestSound = { soundType -> soundAlertViewModel.triggerAlert(soundType) }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Active Alert Banner
        soundAlertUiState?.let { alert ->
            SoundAlertBanner(
                alert = alert,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
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
                items(captionUiState.captionLines) { line ->
                    CaptionLineItem(line = line)
                }
                
                // Partial text (in-progress)
                if (captionUiState.partialText.isNotEmpty()) {
                    item {
                        Text(
                            text = captionUiState.partialText,
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
 * Sound alerts section with toggle and test buttons.
 */
@Composable
private fun SoundAlertsSection(
    isListening: Boolean,
    isModelLoaded: Boolean,
    onToggleListening: () -> Unit,
    onTestSound: (VibrationVocabulary.SoundType) -> Unit
) {
    Column {
        // Section header
        Text(
            text = "Environmental Sound Alerts",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Listening toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Listening",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            
            Switch(
                checked = isListening,
                onCheckedChange = { onToggleListening() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PersonaDeaf,
                    checkedTrackColor = PersonaDeaf.copy(alpha = 0.5f),
                    uncheckedThumbColor = TextPrimary.copy(alpha = 0.5f),
                    uncheckedTrackColor = TextPrimary.copy(alpha = 0.2f)
                )
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Model missing warning
        if (!isModelLoaded) {
            Box(
                modifier = Modifier
                    .background(HighlightBox, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "YAMNet model missing — use test buttons",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        // Test buttons grid
        TestButtonsGrid(onTestSound = onTestSound)
    }
}

/**
 * Grid of test buttons for sound alerts.
 */
@Composable
private fun TestButtonsGrid(onTestSound: (VibrationVocabulary.SoundType) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TestButton(
                text = "Test Siren",
                soundType = VibrationVocabulary.SoundType.SIREN,
                onClick = onTestSound,
                modifier = Modifier.weight(1f)
            )
            TestButton(
                text = "Test Fire Alarm",
                soundType = VibrationVocabulary.SoundType.FIRE_ALARM,
                onClick = onTestSound,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TestButton(
                text = "Test Doorbell",
                soundType = VibrationVocabulary.SoundType.DOORBELL,
                onClick = onTestSound,
                modifier = Modifier.weight(1f)
            )
            TestButton(
                text = "Test Dog Bark",
                soundType = VibrationVocabulary.SoundType.DOG_BARKING,
                onClick = onTestSound,
                modifier = Modifier.weight(1f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TestButton(
                text = "Test Baby Cry",
                soundType = VibrationVocabulary.SoundType.BABY_CRYING,
                onClick = onTestSound,
                modifier = Modifier.weight(1f)
            )
            TestButton(
                text = "Test Car Horn",
                soundType = VibrationVocabulary.SoundType.CAR_HORN,
                onClick = onTestSound,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Individual test button.
 */
@Composable
private fun TestButton(
    text: String,
    soundType: VibrationVocabulary.SoundType,
    onClick: (VibrationVocabulary.SoundType) -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = { onClick(soundType) },
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfaceTint
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

/**
 * Check if microphone permission is granted.
 */
private fun checkMicrophonePermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}