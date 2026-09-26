package com.teamdexters.limitless.ui.persona

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.data.local.entity.PersonaPreferenceEntity
import com.teamdexters.limitless.ui.navigation.Screen
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Persona selection screen where users choose their accessibility mode.
 * Features:
 * - Visual cards for each persona type
 * - Text-to-speech announcement on screen load
 * - Voice input for hands-free selection
 * - Persists selection to Room database
 */
@Composable
fun PersonaSelectScreen(
    navController: NavController,
    database: LimitlessDatabase
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // TTS initialization
    var ttsRef: TextToSpeech? by remember { mutableStateOf(null) }
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef?.language = Locale.US
                ttsRef?.speak(
                    "Choose your assist mode: Blind and Low Vision, Deaf and Hard of Hearing, Speech Impaired, or Mobility and Wheelchair.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "persona_announcement"
                )
            }
        }.also { ttsRef = it }
    }
    
    // Speech recognition setup
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    var isListening by remember { mutableStateOf(false) }
    
    // Permission handling would be handled in Activity
    // For now, we'll create a simplified speech listener stub
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
            speechRecognizer.destroy()
        }
    }
    
    // Start listening for voice input
    LaunchedEffect(Unit) {
        // In a real implementation, we would:
        // 1. Check for RECORD_AUDIO permission
        // 2. Start speech recognition
        // 3. Match spoken results to navigate
        // For now, this is a stub that would be expanded with proper permission handling
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Heading
        Text(
            text = "Limitless Assist",
            style = LimitlessTypography.headlineLarge,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        Text(
            text = "Select your assist mode or speak your choice",
            style = LimitlessTypography.bodyMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Persona Cards Grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Row 1: Blind & Deaf
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonaCard(
                    title = "Blind & Low-Vision",
                    color = PersonaBlind,
                    icon = Icons.Default.Visibility,
                    onClick = {
                        savePersonaAndNavigate("BLIND", navController, database, coroutineScope)
                    },
                    modifier = Modifier.weight(1f)
                )
                
                PersonaCard(
                    title = "Deaf & Hard-of-Hearing",
                    color = PersonaDeaf,
                    icon = Icons.Default.Hearing,
                    onClick = {
                        savePersonaAndNavigate("DEAF", navController, database, coroutineScope)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Row 2: Speech & Mobility
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonaCard(
                    title = "Speech-Impaired",
                    color = PersonaSpeech,
                    icon = Icons.Default.QuestionAnswer,
                    onClick = {
                        savePersonaAndNavigate("SPEECH", navController, database, coroutineScope)
                    },
                    modifier = Modifier.weight(1f)
                )
                
                PersonaCard(
                    title = "Mobility & Wheelchair",
                    color = PersonaMobility,
                    icon = Icons.Default.Accessible,
                    onClick = {
                        savePersonaAndNavigate("MOBILITY", navController, database, coroutineScope)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Composable card for persona selection.
 * Features proper accessibility semantics and high contrast.
 */
@Composable
fun PersonaCard(
    title: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .background(color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                heading()
                contentDescription = "$title. Tap to select this assist mode."
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = LimitlessTypography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Saves the selected persona to Room database and navigates to the appropriate screen.
 */
private fun savePersonaAndNavigate(
    persona: String,
    navController: NavController,
    database: LimitlessDatabase,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            val personaDao = database.personaPreferenceDao()
            personaDao.setPreference(
                PersonaPreferenceEntity(
                    id = 1,
                    selectedPersona = persona,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        
        // Navigate to the appropriate screen based on persona
        val destination = when (persona) {
            "BLIND" -> Screen.BlindHome.route
            "DEAF" -> Screen.DeafHome.route
            "SPEECH" -> Screen.SpeechHome.route
            "MOBILITY" -> Screen.MobilityHome.route
            else -> Screen.PersonaSelect.route
        }
        
        navController.navigate(destination)
    }
}