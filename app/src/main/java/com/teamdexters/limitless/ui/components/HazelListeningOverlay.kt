package com.teamdexters.limitless.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.teamdexters.limitless.assistant.HazelIntent
import com.teamdexters.limitless.assistant.IntentRouter
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "HAZEL_MIC_TRACE"

@Composable
fun HazelListeningOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    intentRouter: IntentRouter,
    onIntentResult: (HazelIntent) -> Unit,
    onShowHistory: () -> Unit = {}
) {
    if (!isVisible) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var statusText by remember { mutableStateOf("Status: Listening... speak your query") }
    var recognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }
    
    val startListening = {
        val recognizer = recognizerRef
        if (recognizer != null) {
            Log.d(TAG, "Calling recognizer.startListening()")
            statusText = "Status: Listening... speak your query"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            }
            recognizer.startListening(intent)
            
            // Auto-cancel after 8 seconds
            coroutineScope.launch {
                delay(8000L)
                recognizer.cancel()
            }
        }
    }

    DisposableEffect(isVisible) {
        if (isVisible) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Overlay opened. Checking RECORD_AUDIO permission: $granted")
            
            if (granted) {
                Log.d("LIMITLESS_TRACE", "[Hazel] Overlay opened. Starting mic...")
                // Must be main thread for SpeechRecognizer
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizerRef = recognizer
                
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onReadyForSpeech")
                        Log.d(TAG, "onReadyForSpeech: Mic hardware active and ready")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onBeginningOfSpeech")
                        Log.d(TAG, "onBeginningOfSpeech: User voice detected!")
                        statusText = "Status: Voice detected..."
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Optional low-freq log
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {
                        Log.d(TAG, "onBufferReceived: Audio bytes receiving")
                    }

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech: User stopped speaking")
                    }

                    override fun onError(error: Int) {
                        val errorName = when (error) {
                            1 -> "NETWORK_TIMEOUT"
                            2 -> "NETWORK"
                            3 -> "AUDIO"
                            4 -> "SERVER"
                            5 -> "CLIENT"
                            6 -> "SPEECH_TIMEOUT"
                            7 -> "NO_MATCH"
                            8 -> "RECOGNIZER_BUSY"
                            9 -> "INSUFFICIENT_PERMISSIONS"
                            else -> "UNKNOWN_$error"
                        }
                        Log.e(TAG, "onError: Code $error ($errorName)")
                        statusText = "Status: Tap mic to speak"
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val capturedText = matches?.firstOrNull()?.trim() ?: ""
                        Log.d("LIMITLESS_TRACE", "SpeechRecognizer: onResults (Captured text: \"$capturedText\")")
                        Log.d(TAG, "onResults: Captured -> $capturedText")
                        statusText = "Status: Heard -> $capturedText"
                        
                        if (capturedText.isNotEmpty()) {
                            val intent = intentRouter.routeIntent(capturedText)
                            onIntentResult(intent)
                            onDismiss()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val capturedText = matches?.firstOrNull()?.trim() ?: ""
                        if (capturedText.isNotEmpty()) {
                            statusText = "Status: Heard -> $capturedText"
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                
                coroutineScope.launch {
                    delay(500L) // Allow UI overlay animation to settle
                    Log.d(TAG, "Auto-starting microphone on overlay open...")
                    startListening()
                }
            } else {
                statusText = "Status: Error (INSUFFICIENT_PERMISSIONS) - Tap mic to retry"
            }
        }
        
        onDispose {
            Log.d("LIMITLESS_TRACE", "[HazelListeningOverlay] Destroy SpeechRecognizer (Overlay closed)")
            recognizerRef?.stopListening()
            recognizerRef?.destroy()
            recognizerRef = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LimitlessBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HighlightBox, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with cancel button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hazel Assistant",
                    style = LimitlessTypography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = { 
                        onDismiss()
                        onShowHistory() 
                    }) {
                        Text("History", color = com.teamdexters.limitless.ui.theme.LimitlessPrimary, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Hazel Assistant",
                            tint = TextPrimary
                        )
                    }
                }
            }
            
            // Visual waveform indicator (flat pastel blocks)
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WaveformBlock(color = PersonaBlind, height = 24.dp)
                WaveformBlock(color = PersonaDeaf, height = 32.dp)
                WaveformBlock(color = PersonaSpeech, height = 20.dp)
                WaveformBlock(color = PersonaMobility, height = 36.dp)
                WaveformBlock(color = PersonaBlind, height = 28.dp)
                WaveformBlock(color = PersonaDeaf, height = 22.dp)
                WaveformBlock(color = PersonaSpeech, height = 30.dp)
            }
            
            // Tap-to-Speech manual trigger button
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(com.teamdexters.limitless.ui.theme.LimitlessPrimary, androidx.compose.foundation.shape.CircleShape)
                    .clickable {
                        Log.d(TAG, "User manually tapped mic icon in overlay")
                        recognizerRef?.cancel()
                        startListening()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to speak again",
                    tint = TextPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to speak",
                style = LimitlessTypography.labelMedium,
                color = TextPrimary
            )
            
            // Transcribed text/Status display
            Text(
                text = statusText,
                style = LimitlessTypography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * Individual block for the visual waveform indicator.
 * Flat pastel colored block with varying heights.
 */
@Composable
fun WaveformBlock(
    color: androidx.compose.ui.graphics.Color,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(height)
            .background(color, RoundedCornerShape(4.dp))
    )
}