package com.teamdexters.limitless.ui.components

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.assistant.HazelIntent
import com.teamdexters.limitless.assistant.IntentRouter
import com.teamdexters.limitless.core.audio.VoiceManager
import com.teamdexters.limitless.core.audio.VoiceResult
import com.teamdexters.limitless.core.audio.VoiceState
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.TextPrimary
import dagger.hilt.android.EntryPointAccessors
import com.teamdexters.limitless.di.VoiceManagerEntryPoint

private const val TAG = "VoiceManager"

/**
 * Hazel Assistant listening overlay.
 *
 * This is a UI-ONLY component. It NEVER calls requestState() or manages VoiceManager state.
 * All state transitions are controlled by VoiceManager and MainActivity.
 * The overlay only displays: waveform, transcript, status, and assistant response.
 */
@Composable
fun HazelListeningOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    intentRouter: IntentRouter,
    onIntentResult: (HazelIntent) -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val voiceManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VoiceManagerEntryPoint::class.java
        ).voiceManager()
    }

    var statusText by remember { mutableStateOf("Listening… speak your query") }
    val voiceState by voiceManager.state.collectAsState()

    // Determine status based on VoiceManager state
    val displayStatus = when (voiceState) {
        VoiceState.ASSISTANT_RECORDING -> "Listening…"
        VoiceState.ASSISTANT_PROCESSING -> "Processing…"
        VoiceState.TTS_PLAYING -> "Speaking…"
        else -> statusText
    }

    // Collect results from VoiceManager for display only
    LaunchedEffect(Unit) {
        voiceManager.results.collect { result ->
            // Only process results when in assistant states
            if (voiceState != com.teamdexters.limitless.core.audio.VoiceState.ASSISTANT_RECORDING &&
                voiceState != com.teamdexters.limitless.core.audio.VoiceState.ASSISTANT_PROCESSING) {
                return@collect
            }

            when (result) {
                is VoiceResult.Partial -> {
                    if (result.text.isNotEmpty()) {
                        statusText = "Heard → ${result.text}"
                    }
                }
                is VoiceResult.Final -> {
                    val text = result.text
                    Log.d(TAG, "OVERLAY: Final result → \"$text\"")
                    if (text.isNotEmpty()) {
                        statusText = "Processing → $text"
                        val intent = intentRouter.routeIntent(text)
                        onIntentResult(intent)
                    }
                }
                is VoiceResult.Error -> {
                    Log.e(TAG, "OVERLAY: error ${result.code} ${result.message}")
                    statusText = "Error: ${result.message}"
                }
            }
        }
    }

    // Overlay lifecycle logging only - NO state changes
    DisposableEffect(isVisible) {
        if (isVisible) {
            Log.d(TAG, "=== OVERLAY_LIFECYCLE_START ===")
            Log.d(TAG, "OVERLAY: UI visible (state=$voiceState)")
        }
        onDispose {
            Log.d(TAG, "=== OVERLAY_LIFECYCLE_STOP ===")
            Log.d(TAG, "OVERLAY: UI hidden (state=$voiceState)")
            // UI-ONLY: No state changes here
            // VoiceManager handles state transitions via conversation timeout or explicit requests from MainActivity
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Hazel Assistant",
                    style      = LimitlessTypography.titleMedium,
                    color      = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector     = Icons.Default.Close,
                        contentDescription = "Close Hazel Assistant",
                        tint            = TextPrimary
                    )
                }
            }

            // Visual waveform
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

            // Tap-to-retry mic button - DISABLED (UI-only overlay)
            // VoiceManager controls state transitions automatically
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(LimitlessPrimary.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "Listening (automatic)",
                    tint               = TextPrimary,
                    modifier           = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text  = "Listening",
                style = LimitlessTypography.labelMedium,
                color = TextPrimary
            )

            Text(
                text     = statusText,
                style    = LimitlessTypography.bodyMedium,
                color    = TextPrimary,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

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
