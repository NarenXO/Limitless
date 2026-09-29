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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Translate
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
import com.teamdexters.limitless.feature.deaf.translation.TranslationViewModel
import com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager
import com.teamdexters.limitless.feature.deaf.speaker.VoiceProfile
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
    soundAlertViewModel: SoundAlertViewModel = viewModel(),
    translationViewModel: TranslationViewModel = viewModel()
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
    
    // Translation state
    val sourceLanguage by translationViewModel.sourceLanguage.collectAsState()
    val targetLanguage by translationViewModel.targetLanguage.collectAsState()
    val downloadStatus by translationViewModel.downloadStatus.collectAsState()
    val isTranslationReady by translationViewModel.isReady.collectAsState()
    val translatedText by translationViewModel.translatedText.collectAsState()
    val supportedLanguages by translationViewModel.supportedLanguages.collectAsState()
    
    // Speaker enrollment state
    val speakerManager = remember { SpeakerEnrollmentManager(context) }
    val enrolledProfiles by speakerManager.enrolledProfiles.collectAsState()
    val isRecording by speakerManager.isRecording.collectAsState()
    val recordingProgress by speakerManager.recordingProgress.collectAsState()
    val enrollmentStatus by speakerManager.enrollmentStatus.collectAsState()
    
    // Speaker enrollment UI state
    var showEnrollmentDialog by remember { mutableStateOf(false) }
    var enrollmentName by remember { mutableStateOf("") }
    var showTranslationPanel by remember { mutableStateOf(false) }
    
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
        try {
            captionViewModel.initialize(context)
        } catch (e: Exception) {
            // If caption initialization fails, app continues without captions
        }
        try {
            soundAlertViewModel.initialize(context)
        } catch (e: Exception) {
            // If sound alert initialization fails, app continues without sound alerts
        }
        try {
            translationViewModel.initialize(context)
        } catch (e: Exception) {
            // If translation initialization fails, app continues without translation
        }
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

            // Top row: Title and Offline capable chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Captions",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
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
                            try {
                                soundAlertViewModel.setAlertEnabled(!isAlertEnabled)
                            } catch (e: Exception) {
                                // Never crash on toggle
                            }
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

                // Sound model error message (smaller, single line)
                AnimatedVisibility(
                    visible = soundErrorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    soundErrorMessage?.let { message ->
                        Row(
                            modifier = Modifier
                                .background(HighlightBox, RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = message,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Manual test buttons (always available, even without model)
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
                            containerColor = SurfaceTint
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
                            containerColor = SurfaceTint
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
                            containerColor = SurfaceTint
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

            Spacer(modifier = Modifier.height(16.dp))

            // Translation Section
            TranslationSection(
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                supportedLanguages = supportedLanguages,
                downloadStatus = downloadStatus,
                isTranslationReady = isTranslationReady,
                translatedText = translatedText,
                showTranslationPanel = showTranslationPanel,
                onTogglePanel = { showTranslationPanel = !showTranslationPanel },
                onSourceLanguageChange = { translationViewModel.setSourceLanguage(it) },
                onTargetLanguageChange = { translationViewModel.setTargetLanguage(it) },
                onTranslate = { text -> translationViewModel.translate(text) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Speaker Enrollment Section
            SpeakerEnrollmentSection(
                enrolledProfiles = enrolledProfiles,
                isRecording = isRecording,
                recordingProgress = recordingProgress,
                enrollmentStatus = enrollmentStatus,
                showEnrollmentDialog = showEnrollmentDialog,
                enrollmentName = enrollmentName,
                onToggleDialog = { showEnrollmentDialog = !showEnrollmentDialog },
                onEnrollmentNameChange = { enrollmentName = it },
                onStartEnrollment = { 
                    if (enrollmentName.isNotBlank()) {
                        coroutineScope.launch {
                            speakerManager.startEnrollment(enrollmentName)
                            enrollmentName = ""
                            showEnrollmentDialog = false
                        }
                    }
                },
                onDeleteProfile = { speakerManager.deleteProfile(it) },
                onResetStatus = { speakerManager.resetEnrollmentStatus() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Error messages (smaller, single line)
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                errorMessage?.let { message ->
                    Row(
                        modifier = Modifier
                            .background(HighlightBox, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = message,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1
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

            // Hazel Response Preview section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hazel Response Preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary.copy(alpha = 0.6f),
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
                                containerColor = SurfaceTint
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Sample ${index + 1}",
                                fontSize = 10.sp,
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
        
        // Speaker Enrollment Dialog
        if (showEnrollmentDialog) {
            EnrollmentDialog(
                enrollmentName = enrollmentName,
                onNameChange = { enrollmentName = it },
                onDismiss = { showEnrollmentDialog = false },
                onConfirm = { 
                    if (enrollmentName.isNotBlank()) {
                        coroutineScope.launch {
                            speakerManager.startEnrollment(enrollmentName)
                            enrollmentName = ""
                            showEnrollmentDialog = false
                        }
                    }
                }
            )
        }
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

/**
 * Translation section with language selectors and translated text display.
 */
@Composable
private fun TranslationSection(
    sourceLanguage: String,
    targetLanguage: String,
    supportedLanguages: List<String>,
    downloadStatus: com.teamdexters.limitless.feature.deaf.translation.OfflineTranslator.DownloadStatus,
    isTranslationReady: Boolean,
    translatedText: String,
    showTranslationPanel: Boolean,
    onTogglePanel: () -> Unit,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit,
    onTranslate: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Translation",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Button(
                onClick = onTogglePanel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceTint
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        if (showTranslationPanel) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Language selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageSelector(
                    label = "Source",
                    selectedLanguage = sourceLanguage,
                    languages = supportedLanguages,
                    onLanguageChange = onSourceLanguageChange,
                    modifier = Modifier.weight(1f)
                )
                
                LanguageSelector(
                    label = "Target",
                    selectedLanguage = targetLanguage,
                    languages = supportedLanguages,
                    onLanguageChange = onTargetLanguageChange,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Download status
            AnimatedVisibility(
                visible = downloadStatus != com.teamdexters.limitless.feature.deaf.translation.OfflineTranslator.DownloadStatus.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val statusText = when (downloadStatus) {
                    com.teamdexters.limitless.feature.deaf.translation.OfflineTranslator.DownloadStatus.Downloading -> "Downloading language model..."
                    com.teamdexters.limitless.feature.deaf.translation.OfflineTranslator.DownloadStatus.Ready -> "Model ready"
                    com.teamdexters.limitless.feature.deaf.translation.OfflineTranslator.DownloadStatus.Error -> "Model download failed"
                    else -> ""
                }
                
                Row(
                    modifier = Modifier
                        .background(SurfaceTint, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Translated text display
            if (translatedText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceTint, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = translatedText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Language selector dropdown.
 */
@Composable
private fun LanguageSelector(
    label: String,
    selectedLanguage: String,
    languages: List<String>,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Button(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceTint
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selectedLanguage,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceTint, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column {
                    languages.forEach { language ->
                        Button(
                            onClick = {
                                onLanguageChange(language)
                                expanded = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (language == selectedLanguage) PersonaDeaf else SurfaceTint
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = language,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Speaker enrollment section.
 */
@Composable
private fun SpeakerEnrollmentSection(
    enrolledProfiles: List<com.teamdexters.limitless.feature.deaf.speaker.VoiceProfile>,
    isRecording: Boolean,
    recordingProgress: Float,
    enrollmentStatus: com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus,
    showEnrollmentDialog: Boolean,
    enrollmentName: String,
    onToggleDialog: () -> Unit,
    onEnrollmentNameChange: (String) -> Unit,
    onStartEnrollment: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onResetStatus: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = SurfaceTint
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Speaker Enrollment",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Button(
                    onClick = onToggleDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PersonaDeaf
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Enroll",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
            
            // Recording progress
            if (isRecording) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recording... ${(recordingProgress * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(PersonaDeaf.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(recordingProgress)
                                .fillMaxHeight()
                                .background(PersonaDeaf, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
            
            // Enrollment status
            AnimatedVisibility(
                visible = enrollmentStatus != com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus.Idle,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val statusText = when (enrollmentStatus) {
                    com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus.Recording -> "Recording voice sample..."
                    com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus.Processing -> "Processing voice profile..."
                    com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus.Success -> "Voice enrolled successfully!"
                    com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus.Error -> "Enrollment failed. Please try again."
                    else -> ""
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .background(HighlightBox, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                
                if (enrollmentStatus == com.teamdexters.limitless.feature.deaf.speaker.SpeakerEnrollmentManager.EnrollmentStatus.Success) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onResetStatus,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceTint
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "OK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
            
            // Enrolled profiles list
            if (enrolledProfiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Enrolled Speakers",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                enrolledProfiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HighlightBox, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        
                        Button(
                            onClick = { onDeleteProfile(profile.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HighlightBox
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Enrollment dialog for recording voice samples.
 */
@Composable
private fun EnrollmentDialog(
    enrollmentName: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enroll Voice",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a name for this voice profile (e.g., \"Mom\", \"Teacher\")",
                    fontSize = 14.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                OutlinedTextField(
                    value = enrollmentName,
                    onValueChange = onNameChange,
                    placeholder = { Text("Name") },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = PersonaDeaf,
                        unfocusedBorderColor = SurfaceTint,
                        cursorColor = PersonaDeaf
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Tap Enroll to record a 4-second voice sample",
                    fontSize = 12.sp,
                    color = TextPrimary.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersonaDeaf
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = enrollmentName.isNotBlank()
            ) {
                Text(
                    text = "Enroll",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceTint
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    )
}
