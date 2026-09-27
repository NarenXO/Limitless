package com.teamdexters.limitless.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamdexters.limitless.feature.deaf.caption.CaptionLine
import com.teamdexters.limitless.feature.deaf.caption.CaptionViewModel
import com.teamdexters.limitless.feature.deaf.sound.SoundAlertViewModel
import com.teamdexters.limitless.feature.deaf.sound.SoundType
import com.teamdexters.limitless.ui.components.SoundAlertBanner
import com.teamdexters.limitless.ui.components.SubtitleOverlay
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Home screen for deaf and hard-of-hearing users with live captions and sound alerts.
 * Features offline speech-to-text using Vosk for real-time captioning and
 * YAMNet-based environmental sound detection for emergency alerts.
 */
@Composable
fun DeafHomeScreen(
    captionViewModel: CaptionViewModel = viewModel(),
    soundAlertViewModel: SoundAlertViewModel = viewModel()
) {
    val context = LocalContext.current
    val captions by captionViewModel.captions.collectAsState()
    val isListening by captionViewModel.isListening.collectAsState()
    val modelLoaded by captionViewModel.modelLoaded.collectAsState()
    val errorMessage by captionViewModel.errorMessage.collectAsState()
    
    // Sound alert state
    val currentAlert by soundAlertViewModel.currentAlert.collectAsState()
    val isAlertEnabled by soundAlertViewModel.isAlertEnabled.collectAsState()
    val soundModelLoaded by soundAlertViewModel.modelLoaded.collectAsState()
    val soundErrorMessage by soundAlertViewModel.errorMessage.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }
    
    // SubtitleOverlay preview test state
    var showSubtitle by remember { mutableStateOf(false) }
    var subtitleText by remember { mutableStateOf("") }
    val sampleResponses = listOf(
        "Hazel: Accessible ramp detected ahead.",
        "Hazel: Emergency exit is behind you.",
        "Hazel: Elevator is to your right on the ground floor."
    )

    // Check mic permission
    val micPermissionGranted = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Initialize models on first composition
    LaunchedEffect(Unit) {
        captionViewModel.initialize(context)
        soundAlertViewModel.initialize(context)
    }

    // Auto-scroll to bottom when new captions arrive
    LaunchedEffect(captions.size) {
        if (captions.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(captions.size - 1)
            }
        }
    }

    // Release resources on dispose
    DisposableEffect(Unit) {
        onDispose {
            captionViewModel.stopListening()
            soundAlertViewModel.clearAlert()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sound Alert Banner
            SoundAlertBanner(
                alert = currentAlert,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Live Captions",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Offline capable chip
            Row(
                modifier = Modifier
                    .background(SurfaceTint, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offline capable",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sound Alerts Toggle Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sound Alerts",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            soundAlertViewModel.setAlertEnabled(!isAlertEnabled)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAlertEnabled) PersonaDeaf else SurfaceTint
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = if (isAlertEnabled) "On" else "Off",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }

                // Sound model error message
                AnimatedVisibility(
                    visible = soundErrorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    soundErrorMessage?.let { message ->
                        Row(
                            modifier = Modifier
                                .background(HighlightBox, RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = message,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // Manual test buttons
                if (soundModelLoaded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Test Alerts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { soundAlertViewModel.triggerTestAlert(SoundType.SIREN) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaDeaf.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Siren",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = { soundAlertViewModel.triggerTestAlert(SoundType.FIRE_ALARM) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaDeaf.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Fire",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = { soundAlertViewModel.triggerTestAlert(SoundType.DOORBELL) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaDeaf.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "Door",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error messages
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let { message ->
                    Row(
                        modifier = Modifier
                            .background(HighlightBox, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = message,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start/Stop button
            Button(
                onClick = {
                    if (isListening) {
                        captionViewModel.stopListening()
                    } else {
                        captionViewModel.startListening(context, micPermissionGranted)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersonaDeaf
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = if (isListening) "Stop" else "Start",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SubtitleOverlay preview section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SubtitleOverlay Preview",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sampleResponses.forEachIndexed { index, response ->
                        Button(
                            onClick = {
                                subtitleText = response
                                showSubtitle = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaDeaf.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(
                                text = "Sample ${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caption display area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(SurfaceTint, RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                if (captions.isEmpty()) {
                    Text(
                        text = "Tap Start to begin live captions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(captions) { caption ->
                            CaptionLineItem(caption = caption)
                        }
                    }
                }
            }
        }

        // SubtitleOverlay preview
        SubtitleOverlay(
            text = subtitleText,
            isVisible = showSubtitle,
            onDismiss = { showSubtitle = false },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

/**
 * Composable for displaying a single caption line.
 */
@Composable
private fun CaptionLineItem(caption: CaptionLine) {
    Text(
        text = caption.text,
        fontSize = 22.sp,
        fontWeight = if (caption.isPartial) FontWeight.Normal else FontWeight.Bold,
        color = TextPrimary,
        lineHeight = 32.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}
