package com.teamdexters.limitless.ui.speech

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.teamdexters.limitless.speech.AACCategoryManager
import com.teamdexters.limitless.speech.AACPhraseCard
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import com.teamdexters.limitless.ui.components.HazelResponseBanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechHomeScreen(
    onBack: () -> Unit = {},
    onNavigateToComposer: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // AAC Manager
    val aacManager = remember { AACCategoryManager() }
    val categories = remember { aacManager.getCategories() }
    
    // State
    var selectedLanguage by rememberSaveable { mutableStateOf("en") }
    var selectedCategory by rememberSaveable { mutableStateOf(categories.first()) }
    
    val currentPhrases = remember(selectedLanguage, selectedCategory) {
        aacManager.getPhraseCards(selectedLanguage, selectedCategory)
    }

    // Banner state
    var bannerText by remember { mutableStateOf("") }
    var isBannerVisible by remember { mutableStateOf(false) }

    // TTS
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        val engineTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.US
                ttsReady = true
            }
        }
        tts.value = engineTts
        onDispose {
            engineTts.stop()
            engineTts.shutdown()
            tts.value = null
        }
    }

    LaunchedEffect(selectedLanguage) {
        tts.value?.let { engineTts ->
            val locale = when (selectedLanguage) {
                "ta" -> Locale("ta", "IN")
                "hi" -> Locale("hi", "IN")
                else -> Locale.US
            }
            val result = engineTts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engineTts.setLanguage(Locale.US)
                Toast.makeText(context, "Voice not installed. Using English.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun speakPhrase(phrase: String) {
        tts.value?.let { engineTts ->
            if (ttsReady && phrase.isNotBlank()) {
                engineTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, phrase)
                bannerText = "Said: \"$phrase\""
                isBannerVisible = true
                
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vibratorManager.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(100)
                    }
                } catch (e: Exception) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                
                scope.launch {
                    delay(3000)
                    isBannerVisible = false
                }
            }
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .zIndex(100f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
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
                            text = "Speech & Communication",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    // Language toggles
                    val languages = listOf("en" to "English", "ta" to "தமிழ்", "hi" to "हिन्दी")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languages.forEach { (code, name) ->
                            val isSelected = selectedLanguage == code
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLanguage = code },
                                label = {
                                    Text(
                                        text = name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PersonaSpeech,
                                    containerColor = SurfaceTint,
                                    selectedLabelColor = TextPrimary,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
                
                HazelResponseBanner(
                    text = bannerText,
                    isVisible = isBannerVisible,
                    onDismiss = { isBannerVisible = false },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToComposer,
                containerColor = PersonaSpeech,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Open Composer")
            }
        },
        containerColor = LimitlessBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "AAC Categories",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PersonaSpeech
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(categories) { _, category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PersonaSpeech,
                            containerColor = SurfaceTint,
                            selectedLabelColor = TextPrimary,
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Grid cards
            val phraseRows = currentPhrases.chunked(2)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                phraseRows.forEach { rowCards ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowCards.forEach { card ->
                            Box(modifier = Modifier.weight(1f)) {
                                var isFocused by remember { mutableStateOf(false) }
                                val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f)
                                
                                Box(
                                    modifier = Modifier
                                        .scale(scale)
                                        .fillMaxWidth()
                                        .background(PersonaSpeech, RoundedCornerShape(16.dp))
                                        .border(
                                            width = if (isFocused) 2.5.dp else 1.dp,
                                            color = if (isFocused) TextPrimary else PersonaSpeech.copy(alpha = 0.30f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .semantics(mergeDescendants = true) {
                                            role = Role.Button
                                            contentDescription = card.text
                                            onClick {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                speakPhrase(card.text)
                                                true
                                            }
                                        }
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            speakPhrase(card.text)
                                        }
                                        .focusable()
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .padding(vertical = 20.dp, horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = card.icon,
                                            contentDescription = null,
                                            tint = TextPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = card.text,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                        if (rowCards.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
        }
    }
}
