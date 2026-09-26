package com.teamdexters.limitless.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.assistant.IntentRouter
import com.teamdexters.limitless.assistant.HazelIntent
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.PersonaMobility
import com.teamdexters.limitless.ui.theme.PersonaSpeech
import com.teamdexters.limitless.ui.theme.TextPrimary

/**
 * Overlay UI for Hazel assistant listening state.
 * Features:
 * - Flat modal with LimitlessBackground and HighlightBox accent
 * - Visual waveform indicator (flat pastel blocks)
 * - Live transcribed text display
 * - Cancel button for switch/accessibility users
 * - Dismissible via tap outside
 * 
 * @param isVisible Whether the overlay should be shown
 * @param transcribedText The currently transcribed text from speech recognition
 * @param onDismiss Callback when the overlay is dismissed
 * @param onIntentResult Callback when an intent is matched and routed
 * @param intentRouter The IntentRouter to analyze spoken text
 */
@Composable
fun HazelListeningOverlay(
    isVisible: Boolean,
    transcribedText: String,
    onDismiss: () -> Unit,
    onIntentResult: (HazelIntent) -> Unit,
    intentRouter: IntentRouter
) {
    if (!isVisible) return
    
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
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Hazel Assistant",
                        tint = TextPrimary
                    )
                }
            }
            
            // Listening status
            Text(
                text = "Hazel is listening...",
                style = LimitlessTypography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            
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
            
            // Mic icon
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Microphone listening",
                tint = TextPrimary,
                modifier = Modifier.size(48.dp)
            )
            
            // Transcribed text display
            if (transcribedText.isNotEmpty()) {
                Text(
                    text = transcribedText,
                    style = LimitlessTypography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(8.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Process the transcribed text through IntentRouter
                val intent = intentRouter.routeIntent(transcribedText)
                onIntentResult(intent)
            }
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