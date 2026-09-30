package com.teamdexters.limitless.ui.blind.nav

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.blind.HapticVocabulary
import com.teamdexters.limitless.ui.blind.TTSManager
import com.teamdexters.limitless.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen navigation overlay for turn-by-turn directions.
 * Displays current step, distance, and next step preview with haptic feedback.
 */
@Composable
fun NavigationOverlay(
    destination: String,
    route: MockRoute,
    ttsManager: TTSManager,
    context: Context,
    onExit: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentStepIndex by remember { mutableStateOf(0) }
    var autoAdvanceJob by remember { mutableStateOf<Job?>(null) }
    
    val currentStep = route.steps[currentStepIndex]
    val isArrived = currentStep.turnType == TurnType.ARRIVE
    
    // Trigger step behavior (TTS + haptics) when step changes
    LaunchedEffect(currentStepIndex) {
        if (autoAdvanceJob != null) {
            autoAdvanceJob?.cancel()
            autoAdvanceJob = null
        }
        
        // Trigger haptic feedback based on turn type
        val hapticEvent = when (currentStep.turnType) {
            TurnType.LEFT -> HapticVocabulary.HapticEvent.OBJECT_LEFT
            TurnType.RIGHT -> HapticVocabulary.HapticEvent.OBJECT_RIGHT
            TurnType.STRAIGHT -> HapticVocabulary.HapticEvent.OBJECT_CENTER
            TurnType.ARRIVE -> HapticVocabulary.HapticEvent.DESTINATION_REACHED
        }
        HapticVocabulary.trigger(context, hapticEvent)
        
        // Speak instruction
        if (isArrived) {
            ttsManager.speak("You have arrived at your destination.")
        } else {
            ttsManager.speak(currentStep.instruction)
        }
        
        // Auto-advance after TTS + 1.5s pause (max 4s fallback)
        if (!isArrived) {
            autoAdvanceJob = scope.launch {
                // Wait for TTS to finish, but max 2.5s wait
                val ttsStartTime = System.currentTimeMillis()
                while ((System.currentTimeMillis() - ttsStartTime) < 2500) {
                    delay(100)
                }
                // Add 1.5s pause after TTS (total max 4s)
                delay(1500)
                // Check if still on this step before advancing
                if (currentStepIndex < route.steps.size - 1) {
                    currentStepIndex++
                }
            }
        }
    }
    
    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            autoAdvanceJob?.cancel()
            ttsManager.stop()
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Step counter (top-right)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = "Step ${currentStepIndex + 1} of ${route.steps.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Step card
            Surface(
                color = PersonaBlind,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Turn icon
                    TurnIcon(
                        turnType = currentStep.turnType,
                        modifier = Modifier.size(96.dp),
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Current step instruction
                    Text(
                        text = currentStep.instruction,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 24.sp,
                        color = TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Remaining distance
                    if (!isArrived) {
                        Text(
                            text = "${currentStep.distanceMeters} m",
                            style = MaterialTheme.typography.displaySmall,
                            fontSize = 48.sp,
                            color = TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Next step preview
                    if (currentStepIndex < route.steps.size - 1) {
                        val nextStep = route.steps[currentStepIndex + 1]
                        Text(
                            text = "Next: ${nextStep.instruction.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Primary "Next Step" button
            Button(
                onClick = {
                    if (!isArrived && currentStepIndex < route.steps.size - 1) {
                        // Cancel auto-advance job
                        autoAdvanceJob?.cancel()
                        autoAdvanceJob = null
                        // Advance to next step
                        currentStepIndex++
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                enabled = !isArrived,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PersonaBlind,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isArrived) "Arrived" else "Next Step",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 20.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Secondary "Exit Navigation" button
            Button(
                onClick = {
                    autoAdvanceJob?.cancel()
                    ttsManager.stop()
                    onExit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceTint,
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Exit Navigation",
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 16.sp
                )
            }
        }
    }
}
