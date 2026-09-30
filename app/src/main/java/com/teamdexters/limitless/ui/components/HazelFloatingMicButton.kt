package com.teamdexters.limitless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.theme.PersonaBlind
import com.teamdexters.limitless.ui.theme.TextPrimary

/**
 * Floating action button for triggering assistant.
 * Features:
 * - Flat styling using PersonaBlind color
 * - 2D flat mic icon with TextPrimary tint
 * - Minimum 72dp size for accessibility
 * - Proper TalkBack content description
 *
 * @param onClick Callback when the button is clicked
 * @param modifier Optional modifier for positioning and sizing
 */
@Composable
fun HazelFloatingMicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .background(PersonaBlind, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Ask AI assistant with voice command"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Microphone icon for voice commands",
            tint = TextPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}