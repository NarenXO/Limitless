package com.teamdexters.limitless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.border
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary
import com.teamdexters.limitless.util.NetworkStatus

/**
 * Unobtrusive, flat, accessible network status badge composable.
 *
 * Displays the current network connectivity status as a small pill/chip:
 * - **Offline**: HighlightBox (#FFDBDF) background, CloudOff icon, "Offline Mode" text.
 * - **Online**: SurfaceTint (#E0F2F4) background, Cloud icon, "Online" text.
 *
 * Fully accessible with TalkBack via [LiveRegionMode.Polite] semantics,
 * which announces state changes without interrupting the current TalkBack focus.
 *
 * @param networkStatus Current [NetworkStatus] from [NetworkStatusTracker].
 * @param modifier Optional layout modifier.
 */
@Composable
fun NetworkStatusBadge(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val networkStatusTracker = androidx.compose.runtime.remember { com.teamdexters.limitless.util.NetworkStatusTracker(context) }
    
    androidx.compose.runtime.DisposableEffect(networkStatusTracker) {
        networkStatusTracker.register()
        onDispose {
            networkStatusTracker.unregister()
        }
    }
    
    val networkStatus by networkStatusTracker.statusFlow.collectAsState()
    val isOnline = networkStatus is com.teamdexters.limitless.util.NetworkStatus.Online

    val backgroundColor = if (isOnline) com.teamdexters.limitless.ui.theme.SoftWhite else com.teamdexters.limitless.ui.theme.HighlightBox
    val borderColor = if (isOnline) com.teamdexters.limitless.ui.theme.SubtleDivider else com.teamdexters.limitless.ui.theme.HighlightBox
    val icon = if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff
    val label = if (isOnline) "● Online" else "● Offline Mode"
    val accessibilityLabel = if (isOnline) "Network status: Online" else "Network status: Offline Mode"

    Row(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics {
                contentDescription = accessibilityLabel
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = com.teamdexters.limitless.ui.theme.PureBlack,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = LimitlessTypography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            ),
            color = com.teamdexters.limitless.ui.theme.PureBlack
        )
    }
}
