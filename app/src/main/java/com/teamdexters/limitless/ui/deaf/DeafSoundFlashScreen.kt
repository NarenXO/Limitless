package com.teamdexters.limitless.ui.deaf

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamdexters.limitless.feature.deaf.sound.SoundAlertViewModel
import com.teamdexters.limitless.feature.deaf.sound.VibrationVocabulary
import com.teamdexters.limitless.ui.theme.manropeFontFamily

/**
 * Data structure for sound flash cards.
 */
data class SoundCard(
    val label: String,
    val alertBanner: String,
    val borderColor: Color,
    val flashColor: Color,
    val vibrationPattern: LongArray
)

/**
 * Sound flash cards screen for deaf users.
 * Displays 6 distinct sound cards with unique visual flash colors and vibration patterns.
 * Includes live environmental sound detection toggle.
 */
@Composable
fun DeafSoundFlashScreen(viewModel: SoundAlertViewModel = viewModel()) {
    val context = LocalContext.current
    
    var activeCard by remember { mutableStateOf<SoundCard?>(null) }
    var isFlashActive by remember { mutableStateOf(false) }
    
    // Permission launcher for RECORD_AUDIO
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleListening()
        }
    }
    
    // ViewModel state
    val isListening by viewModel.isListening.collectAsState()
    val isModelLoaded by viewModel.isModelLoaded.collectAsState()
    val activeAlert by viewModel.activeAlert.collectAsState()
    
    // Handle detected alerts from live listening
    LaunchedEffect(activeAlert) {
        if (activeAlert != null && !isFlashActive) {
            val matchingCard = getSoundCardForType(activeAlert!!.soundType)
            if (matchingCard != null) {
                activeCard = matchingCard
                isFlashActive = true
                vibratePattern(context, matchingCard.vibrationPattern)
                Log.d("LIMITLESS_TRACE", "DeafSoundFlashScreen: Live alert triggered -> ${matchingCard.label}")
                
                // Auto-clear after 2 seconds
                kotlinx.coroutines.delay(2000)
                isFlashActive = false
                activeCard = null
                viewModel.dismissAlert()
            }
        }
    }
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFlashActive && activeCard != null) {
            activeCard!!.flashColor
        } else {
            Color(0xFFF7F1EE)
        },
        label = "background_flash"
    )
    
    val soundCards = listOf(
        SoundCard(
            label = "Fire Alarm",
            alertBanner = "[SOUND ALERT: FIRE ALARM DETECTED]",
            borderColor = Color(0xFFD32F2F),
            flashColor = Color(0xFFFFCDD2),
            vibrationPattern = longArrayOf(0, 1000)
        ),
        SoundCard(
            label = "Emergency Siren",
            alertBanner = "[SOUND ALERT: EMERGENCY SIREN DETECTED]",
            borderColor = Color(0xFFF57C00),
            flashColor = Color(0xFFFFE0B2),
            vibrationPattern = longArrayOf(0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100)
        ),
        SoundCard(
            label = "Doorbell",
            alertBanner = "[SOUND ALERT: DOORBELL DETECTED]",
            borderColor = Color(0xFF1976D2),
            flashColor = Color(0xFFBBDEFB),
            vibrationPattern = longArrayOf(0, 200, 200, 200)
        ),
        SoundCard(
            label = "Baby Crying",
            alertBanner = "[SOUND ALERT: BABY CRYING DETECTED]",
            borderColor = Color(0xFFC2185B),
            flashColor = Color(0xFFF8BBD0),
            vibrationPattern = longArrayOf(0, 150, 150, 150, 150, 150)
        ),
        SoundCard(
            label = "Car Horn",
            alertBanner = "[SOUND ALERT: CAR HORN DETECTED]",
            borderColor = Color(0xFFFBC02D),
            flashColor = Color(0xFFFFF9C4),
            vibrationPattern = longArrayOf(0, 500)
        ),
        SoundCard(
            label = "Dog Barking",
            alertBanner = "[SOUND ALERT: DOG BARKING DETECTED]",
            borderColor = Color(0xFF7B1FA2),
            flashColor = Color(0xFFE1BEE7),
            vibrationPattern = longArrayOf(0, 100, 80, 100, 80, 100, 80, 100)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Live Listening Toggle Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0F2F4))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Environmental Listening",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F1F1F),
                        fontFamily = manropeFontFamily
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isListening) "ACTIVE" else "PAUSED",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isListening) Color(0xFFF791A9) else Color(0xFF1F1F1F),
                            fontFamily = manropeFontFamily,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        
                        Switch(
                            checked = isListening,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.toggleListening()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFF791A9),
                                checkedTrackColor = Color(0xFFE0F2F4),
                                uncheckedThumbColor = Color(0xFF1F1F1F),
                                uncheckedTrackColor = Color(0xFFE0F2F4)
                            )
                        )
                    }
                }
            }
            
            if (isFlashActive && activeCard != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC1F1F1F))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeCard!!.alertBanner,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF7F1EE),
                        fontFamily = manropeFontFamily
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(soundCards.size) { index ->
                    SoundCardItem(
                        card = soundCards[index],
                        onTap = {
                            activeCard = soundCards[index]
                            isFlashActive = true
                            vibratePattern(context, soundCards[index].vibrationPattern)
                            Log.d("LIMITLESS_TRACE", "DeafSoundFlashScreen: Card tapped -> ${soundCards[index].label}")
                            
                            // Reset flash after 1.5 seconds
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isFlashActive = false
                                activeCard = null
                            }, 1500)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Individual sound card item.
 */
@Composable
fun SoundCardItem(
    card: SoundCard,
    onTap: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(
                color = Color(0xFFE0F2F4),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                border = BorderStroke(3.dp, card.borderColor),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = card.label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1F1F1F),
            fontFamily = manropeFontFamily
        )
    }
}

/**
 * Vibrate device with specific pattern.
 * Uses VibratorManager for API 31+, fallback to getSystemService for older versions.
 */
private fun vibratePattern(context: Context, pattern: LongArray) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    } catch (e: Exception) {
        Log.e("LIMITLESS_TRACE", "DeafSoundFlashScreen: Vibration failed", e)
    }
}

/**
 * Map VibrationVocabulary.SoundType to SoundCard.
 */
private fun getSoundCardForType(soundType: VibrationVocabulary.SoundType): SoundCard? {
    return when (soundType) {
        VibrationVocabulary.SoundType.FIRE_ALARM -> SoundCard(
            label = "Fire Alarm",
            alertBanner = "[SOUND ALERT: FIRE ALARM DETECTED]",
            borderColor = Color(0xFFD32F2F),
            flashColor = Color(0xFFFFCDD2),
            vibrationPattern = longArrayOf(0, 1000)
        )
        VibrationVocabulary.SoundType.SIREN -> SoundCard(
            label = "Emergency Siren",
            alertBanner = "[SOUND ALERT: EMERGENCY SIREN DETECTED]",
            borderColor = Color(0xFFF57C00),
            flashColor = Color(0xFFFFE0B2),
            vibrationPattern = longArrayOf(0, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100)
        )
        VibrationVocabulary.SoundType.DOORBELL -> SoundCard(
            label = "Doorbell",
            alertBanner = "[SOUND ALERT: DOORBELL DETECTED]",
            borderColor = Color(0xFF1976D2),
            flashColor = Color(0xFFBBDEFB),
            vibrationPattern = longArrayOf(0, 200, 200, 200)
        )
        VibrationVocabulary.SoundType.BABY_CRYING -> SoundCard(
            label = "Baby Crying",
            alertBanner = "[SOUND ALERT: BABY CRYING DETECTED]",
            borderColor = Color(0xFFC2185B),
            flashColor = Color(0xFFF8BBD0),
            vibrationPattern = longArrayOf(0, 150, 150, 150, 150, 150)
        )
        VibrationVocabulary.SoundType.CAR_HORN -> SoundCard(
            label = "Car Horn",
            alertBanner = "[SOUND ALERT: CAR HORN DETECTED]",
            borderColor = Color(0xFFFBC02D),
            flashColor = Color(0xFFFFF9C4),
            vibrationPattern = longArrayOf(0, 500)
        )
        VibrationVocabulary.SoundType.DOG_BARKING -> SoundCard(
            label = "Dog Barking",
            alertBanner = "[SOUND ALERT: DOG BARKING DETECTED]",
            borderColor = Color(0xFF7B1FA2),
            flashColor = Color(0xFFE1BEE7),
            vibrationPattern = longArrayOf(0, 100, 80, 100, 80, 100, 80, 100)
        )
    }
}
