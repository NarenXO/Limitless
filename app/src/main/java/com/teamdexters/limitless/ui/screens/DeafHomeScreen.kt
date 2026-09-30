package com.teamdexters.limitless.ui.screens

import android.app.Application
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamdexters.limitless.feature.deaf.caption.CaptionEngineStatus
import com.teamdexters.limitless.feature.deaf.caption.CaptionViewModel
import com.teamdexters.limitless.feature.deaf.sound.SoundAlertViewModel
import com.teamdexters.limitless.feature.deaf.sound.VibrationVocabulary
import com.teamdexters.limitless.feature.deaf.translation.TranslationViewModel
import com.teamdexters.limitless.feature.deaf.camera.LabelOcrScanner
import com.teamdexters.limitless.feature.deaf.camera.AutoFlashlightUtility
import com.teamdexters.limitless.feature.deaf.components.SubtitleOverlay
import com.teamdexters.limitless.ui.components.SoundAlertBanner
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Deaf & Hard of Hearing Assistant - Live Captions Screen
 * Provides real-time offline speech-to-text captions using Vosk.
 * Integrated with YAMNet sound alerts and vibration vocabulary.
 * Integrated with ML Kit offline translation.
 */
@Composable
fun DeafHomeScreen(
    captionViewModel: CaptionViewModel = viewModel(factory = CaptionViewModelFactory(LocalContext.current)),
    soundAlertViewModel: SoundAlertViewModel = viewModel(factory = SoundAlertViewModelFactory(LocalContext.current)),
    translationViewModel: TranslationViewModel = viewModel(
        factory = TranslationViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val context = LocalContext.current
    val captionUiState by captionViewModel.uiState.collectAsState()
    val soundAlertUiState by soundAlertViewModel.activeAlert.collectAsState()
    val isListening by soundAlertViewModel.isListening.collectAsState()
    val isModelLoaded by soundAlertViewModel.isModelLoaded.collectAsState()
    val translationUiState by translationViewModel.uiState.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    var hasMicPermission by remember {
        mutableStateOf(checkMicrophonePermission(context))
    }
    
    // Label OCR Scanner state
    val labelScanner = remember { try { LabelOcrScanner(context) } catch (e: Exception) { null } }
    val recognizedText by (labelScanner?.recognizedText ?: MutableStateFlow("")).collectAsState()
    val isProcessing by (labelScanner?.isProcessing ?: MutableStateFlow(false)).collectAsState()
    val ttsReady by (labelScanner?.ttsReady ?: MutableStateFlow(false)).collectAsState()
    var showCameraView by remember { mutableStateOf(false) }
    
    // Auto-Flashlight Utility state
    val autoFlashlight = remember { try { AutoFlashlightUtility() } catch (e: Exception) { null } }
    val isLowLight by remember { mutableStateOf(false) }
    val averageBrightness by remember { mutableStateOf(0) }
    
    // SubtitleOverlay demo state
    var showSubtitleOverlay by remember { mutableStateOf(false) }
    var subtitleText by remember { mutableStateOf("") }
    
    // Auto-scroll to latest caption
    LaunchedEffect(captionUiState.captionLines.size, captionUiState.partialText) {
        try {
            if (captionUiState.captionLines.isNotEmpty() || captionUiState.partialText.isNotEmpty()) {
                coroutineScope.launch {
                    try {
                        lazyListState.animateScrollToItem(
                            index = captionUiState.captionLines.size + if (captionUiState.partialText.isNotEmpty()) 1 else 0
                        )
                    } catch (e: Exception) {
                        // Ignore scroll errors
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore auto-scroll errors
        }
    }
    
    // Auto-translate latest caption
    LaunchedEffect(captionUiState.captionLines.lastOrNull()?.text) {
        try {
            val latestText = captionUiState.captionLines.lastOrNull()?.text
            if (!latestText.isNullOrEmpty()) {
                translationViewModel.translateText(latestText)
            }
        } catch (e: Exception) {
            // Ignore translation errors
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
            CaptionEngineStatus.IDLE -> {
                Box(
                    modifier = Modifier
                        .background(SurfaceTint, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Caption model ready",
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
                try {
                    if (captionUiState.status == CaptionEngineStatus.LISTENING) {
                        captionViewModel.stopListening()
                    } else {
                        if (!hasMicPermission) {
                            hasMicPermission = checkMicrophonePermission(context)
                        }
                        captionViewModel.startListening()
                    }
                } catch (e: Exception) {
                    // Handle button click errors gracefully
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
            onToggleListening = { try { soundAlertViewModel.toggleListening() } catch (e: Exception) {} },
            onTestSound = { soundType -> try { soundAlertViewModel.triggerAlert(soundType) } catch (e: Exception) {} }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Live Translation Section
        LiveTranslationSection(
            translationUiState = translationUiState,
            onSourceLanguageChange = { language -> try { translationViewModel.setSourceLanguage(language) } catch (e: Exception) {} },
            onTargetLanguageChange = { language -> try { translationViewModel.setTargetLanguage(language) } catch (e: Exception) {} }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Smart Camera Tools Section
        SmartCameraToolsSection(
            showCameraView = showCameraView,
            onToggleCameraView = { showCameraView = !showCameraView },
            recognizedText = recognizedText,
            isProcessing = isProcessing,
            ttsReady = ttsReady,
            isLowLight = isLowLight,
            averageBrightness = averageBrightness,
            onReadText = { try { labelScanner?.readTextAloud(recognizedText) } catch (e: Exception) {} },
            onStopReading = { try { labelScanner?.stopReading() } catch (e: Exception) {} },
            onClearText = { try { labelScanner?.clearText() } catch (e: Exception) {} }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // SubtitleOverlay Demo Section
        SubtitleOverlayDemoSection(
            showOverlay = showSubtitleOverlay,
            subtitleText = subtitleText,
            onToggleOverlay = { 
                showSubtitleOverlay = !showSubtitleOverlay
                if (showSubtitleOverlay) {
                    subtitleText = "This is a subtitle overlay demo text"
                }
            },
            onDismiss = { showSubtitleOverlay = false }
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
        
        // Model status indicator
        if (isModelLoaded) {
            Box(
                modifier = Modifier
                    .background(SurfaceTint, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "YAMNet Sound Alerts Active",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
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
 * Live translation section with language selectors and side-by-side display.
 */
@Composable
private fun LiveTranslationSection(
    translationUiState: com.teamdexters.limitless.feature.deaf.translation.TranslationUiState,
    onSourceLanguageChange: (String) -> Unit,
    onTargetLanguageChange: (String) -> Unit
) {
    val supportedLanguages = listOf("English", "Tamil", "Hindi", "Spanish", "French")
    
    Column {
        // Section header
        Text(
            text = "Live Offline Translation",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Language selectors row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Source language selector
            LanguageSelector(
                label = "From",
                selectedLanguage = translationUiState.sourceLanguage,
                languages = supportedLanguages,
                onLanguageChange = onSourceLanguageChange,
                modifier = Modifier.weight(1f)
            )
            
            // Target language selector
            LanguageSelector(
                label = "To",
                selectedLanguage = translationUiState.targetLanguage,
                languages = supportedLanguages,
                onLanguageChange = onTargetLanguageChange,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Model status indicator
        when (translationUiState.modelStatus) {
            com.teamdexters.limitless.feature.deaf.translation.ModelStatus.DOWNLOADING -> {
                Box(
                    modifier = Modifier
                        .background(HighlightBox, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Downloading ${translationUiState.targetLanguage} model...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
            com.teamdexters.limitless.feature.deaf.translation.ModelStatus.READY -> {
                Box(
                    modifier = Modifier
                        .background(SurfaceTint, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Offline Translation Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
            com.teamdexters.limitless.feature.deaf.translation.ModelStatus.ERROR -> {
                Box(
                    modifier = Modifier
                        .background(SurfaceTint, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Offline Translation Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Side-by-side / stacked display
        if (translationUiState.originalText.isNotEmpty()) {
            // Original text container
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceTint
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Original (${translationUiState.sourceLanguage})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = translationUiState.originalText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Translated text container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, PersonaDeaf, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceTint
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Translated (${translationUiState.targetLanguage})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = PersonaDeaf
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = translationUiState.translatedText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
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
            color = TextPrimary.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selectedLanguage == languages.first()) PersonaDeaf else SurfaceTint,
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedLanguage,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                
                // Simple dropdown trigger
                Button(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = TextPrimary
                    )
                }
            }
        }
        
        // Dropdown menu
        if (expanded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = SurfaceTint
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(4.dp)
                ) {
                    languages.forEach { language ->
                        Button(
                            onClick = {
                                onLanguageChange(language)
                                expanded = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (language == selectedLanguage) PersonaDeaf else SurfaceTint
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
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
 * Smart Camera Tools section with label OCR and auto-flashlight.
 */
@Composable
private fun SmartCameraToolsSection(
    showCameraView: Boolean,
    onToggleCameraView: () -> Unit,
    recognizedText: String,
    isProcessing: Boolean,
    ttsReady: Boolean,
    isLowLight: Boolean,
    averageBrightness: Int,
    onReadText: () -> Unit,
    onStopReading: () -> Unit,
    onClearText: () -> Unit
) {
    Column {
        // Section header
        Text(
            text = "Smart Camera Tools",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Open Label Reader button
        Button(
            onClick = onToggleCameraView,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PersonaDeaf
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (showCameraView) "Close Label Reader" else "Open Label Reader",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        if (showCameraView) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Low Light Indicator Chip
            if (isLowLight) {
                Box(
                    modifier = Modifier
                        .background(HighlightBox, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Low Light — Torch Auto-Enabled (Brightness: $averageBrightness)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Safety Banner
            Box(
                modifier = Modifier
                    .background(SurfaceTint, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Reads printed text on labels only. Does NOT visually identify pills or medication.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Camera placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(SurfaceTint, RoundedCornerShape(12.dp))
                    .border(1.dp, PersonaDeaf, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isProcessing) "Scanning..." else "Camera Preview",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    if (isProcessing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Point camera at label",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // TTS Readout button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onReadText,
                    modifier = Modifier.weight(1f),
                    enabled = ttsReady && recognizedText.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (ttsReady && recognizedText.isNotEmpty()) PersonaDeaf else SurfaceTint
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Read Aloud",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
                
                Button(
                    onClick = onStopReading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceTint
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Stop Reading",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Clear text button
            Button(
                onClick = onClearText,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceTint
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Clear Text",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Recognized text display
            if (recognizedText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = SurfaceTint
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Recognized Text",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = recognizedText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

/**
 * SubtitleOverlay demo section for testing the reusable component.
 */
@Composable
private fun SubtitleOverlayDemoSection(
    showOverlay: Boolean,
    subtitleText: String,
    onToggleOverlay: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        // Section header
        Text(
            text = "Subtitle Overlay Demo",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Toggle button
        Button(
            onClick = onToggleOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PersonaDeaf
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (showOverlay) "Hide Overlay" else "Show Overlay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Floating overlay
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            SubtitleOverlay(
                subtitle = subtitleText,
                isVisible = showOverlay,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * Check if microphone permission is granted.
 */
private fun checkMicrophonePermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

/**
 * Factory for creating CaptionViewModel with context.
 */
private class CaptionViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CaptionViewModel::class.java)) {
            return CaptionViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Factory for creating SoundAlertViewModel with context.
 */
private class SoundAlertViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SoundAlertViewModel::class.java)) {
            return SoundAlertViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Factory for creating TranslationViewModel with application context.
 */
private class TranslationViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranslationViewModel::class.java)) {
            return TranslationViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}