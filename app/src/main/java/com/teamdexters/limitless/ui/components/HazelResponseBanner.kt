package com.teamdexters.limitless.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
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
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * Accessible subtitle mirror component that displays Hazel's spoken responses.
 * Features:
 * - Flat styling with HighlightBox background and LimitlessPrimary border
 * - 2D flat speaker icon
 * - Spoken text mirror using TextPrimary color and Manrope Medium typography
 * - Auto-dismisses after 6 seconds or on click tap
 * - Configured with polite accessibility liveRegion semantics for TalkBack
 *
 * @param text Spoken response text to display in the banner
 * @param isVisible Whether the banner is visible
 * @param onDismiss Callback when banner is dismissed
 * @param modifier Optional modifier
 */
@Composable
fun HazelResponseBanner(
    text: String,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss after 6 seconds
    LaunchedEffect(isVisible, text) {
        if (isVisible && text.isNotEmpty()) {
            delay(6000L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = isVisible && text.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(HighlightBox, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceTint, RoundedCornerShape(12.dp))
                .clickable { onDismiss() }
                .padding(16.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Assistant response: $text"
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Speaker icon indicating spoken response",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = text,
                style = LimitlessTypography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
