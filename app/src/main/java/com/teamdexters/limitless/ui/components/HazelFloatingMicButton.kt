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
import com.teamdexters.limitless.ui.theme.PureBlack
import com.teamdexters.limitless.ui.theme.PureWhite

/**
 * Floating action button for triggering Hazel assistant.
 * Features:
 * - Solid PureBlack (#000000) circle background
 * - PureWhite (#FFFFFF) mic icon — clearly visible on black
 * - 56dp minimum size for accessibility
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
            .size(56.dp)
            .background(PureBlack, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Ask Hazel Assistant"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = PureWhite,   // White icon on black circle — crisp and visible
            modifier = Modifier.size(28.dp)
        )
    }
}