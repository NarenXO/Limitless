package com.teamdexters.limitless.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.PersonaDeaf
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.delay

// TODO(Naren): SubtitleOverlay component is ready. Wire this into HazelQueryHandler response pipeline during final integration.

/**
 * Reusable subtitle overlay component for displaying assistant responses.
 * Designed specifically for deaf and hard-of-hearing users with high contrast styling.
 * 
 * Features:
 * - Flat styling with SurfaceTint background and PersonaDeaf border
 * - Header label "Hazel Response" with flat 2D icon
 * - Dismiss button for manual dismissal
 * - Auto-dismiss after 5 seconds (configurable)
 * - Flat fade-in/out and slide animations only
 * - TalkBack accessibility with Assertive live region
 *
 * @param text Response text to display
 * @param isVisible Whether the overlay is visible
 * @param onDismiss Callback when overlay is dismissed
 * @param modifier Optional modifier
 * @param autoDismissMs Auto-dismiss delay in milliseconds (default 5000ms)
 */
@Composable
fun SubtitleOverlay(
    text: String,
    isVisible: Boolean,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 5000L
) {
    // Auto-dismiss after specified time
    LaunchedEffect(isVisible, text) {
        if (isVisible && text.isNotEmpty()) {
            delay(autoDismissMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isVisible && text.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(SurfaceTint, RoundedCornerShape(16.dp))
                .border(1.5.dp, PersonaDeaf, RoundedCornerShape(16.dp))
                .semantics {
                    liveRegion = LiveRegionMode.Assertive
                    contentDescription = "Hazel response: $text"
                }
        ) {
            // Header with icon and label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Message,
                        contentDescription = null,
                        tint = PersonaDeaf,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Hazel Response",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PersonaDeaf
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Response text
            Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
