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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.teamdexters.limitless.ui.theme.HighlightBox
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
fun SpeechComposerScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var composerText by remember { mutableStateOf("") }
    
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

    // 6 Emotion Cards: Happy, Sad, Angry, Confused, Tired, Excited
    val emotions = listOf(
        Pair("Happy", Icons.Default.SentimentSatisfied),
        Pair("Sad", Icons.Default.SentimentDissatisfied),
        Pair("Angry", Icons.Default.SentimentVeryDissatisfied),
        Pair("Confused", Icons.Default.Help),
        Pair("Tired", Icons.Default.Bedtime),
        Pair("Excited", Icons.Default.Star)
    )

    // Predictive N-gram chips
    val ngramOptions = when (composerText.trim()) {
        "" -> listOf("I need", "Where is", "Please help", "Thank you")
        "I need" -> listOf("water", "food", "a doctor", "restroom", "a chair")
        "Where is" -> listOf("the ramp?", "the elevator?", "the entrance?", "the exit?")
        else -> emptyList()
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
                        text = "Phrase Composer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                
                HazelResponseBanner(
                    text = bannerText,
                    isVisible = isBannerVisible,
                    onDismiss = { isBannerVisible = false },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        },
        containerColor = LimitlessBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Emotions",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PersonaSpeech
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(end = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(emotions) { _, emotion ->
                    var isFocused by remember { mutableStateOf(false) }
                    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f)

                    Box(
                        modifier = Modifier
                            .scale(scale)
                            .width(104.dp)
                            .height(104.dp)
                            .background(SurfaceTint, RoundedCornerShape(16.dp))
                            .border(
                                width = if (isFocused) 2.5.dp else 1.dp,
                                color = if (isFocused) PersonaSpeech else PersonaSpeech.copy(alpha = 0.30f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                composerText = "I am feeling ${emotion.first.lowercase()}. " + composerText
                            }
                            .focusable()
                            .onFocusChanged { isFocused = it.isFocused }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = emotion.second,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = emotion.first,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Composer",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = PersonaSpeech
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TextField(
                value = composerText,
                onValueChange = { composerText = it },
                placeholder = {
                    Text("Build a phrase...", color = TextPrimary.copy(alpha = 0.6f))
                },
                textStyle = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = HighlightBox,
                    unfocusedContainerColor = HighlightBox,
                    focusedIndicatorColor = PersonaSpeech,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (ngramOptions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(ngramOptions) { _, chipText ->
                        AssistChip(
                            onClick = { 
                                composerText = if (composerText.isEmpty()) {
                                    chipText
                                } else {
                                    "$composerText $chipText"
                                }
                            },
                            label = { 
                                Text(
                                    text = chipText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = SurfaceTint,
                                labelColor = TextPrimary
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { speakPhrase(composerText) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersonaSpeech,
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Speak Sentence",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
