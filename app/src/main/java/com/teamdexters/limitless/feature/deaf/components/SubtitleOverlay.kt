package com.teamdexters.limitless.feature.deaf.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary

// Ready to be wired into Hazel's response pipeline at integration by Naren.

/**
 * Reusable SubtitleOverlay component for displaying floating text overlays.
 * 
 * Takes any response string (such as Hazel's spoken assistant responses) and displays it
 * floating on screen with high-contrast text styling and clean fade/slide animations
 * for deaf and hard-of-hearing users.
 * 
 * @param subtitle The text to display in the overlay
 * @param isVisible Whether the overlay should be visible
 * @param modifier Modifier for the overlay container
 * @param onDismiss Optional callback when overlay is dismissed
 */
@Composable
fun SubtitleOverlay(
    subtitle: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 300)
        ) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 300)
        ) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = SurfaceTint,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subtitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

/**
 * SubtitleOverlay with PersonaDeaf accent border for emphasis.
 * 
 * @param subtitle The text to display in the overlay
 * @param isVisible Whether the overlay should be visible
 * @param modifier Modifier for the overlay container
 * @param onDismiss Optional callback when overlay is dismissed
 */
@Composable
fun SubtitleOverlayAccent(
    subtitle: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(
            animationSpec = tween(durationMillis = 300)
        ) + slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = 300)
        ) + slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(durationMillis = 300)
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = SurfaceTint,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = SurfaceTint,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = subtitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
