package com.teamdexters.limitless.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.navigation.Screen
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary

/**
 * Shared quick-access tools navigation bar available on all persona homes.
 * Provides accessible shortcuts to the Accessibility Scanner and Community Reports modules.
 *
 * Features:
 * - SurfaceTint background with LimitlessPrimary border
 * - 2D flat vector icons only (QrCodeScanner and Groups)
 * - Accessible minimum 48dp touch targets
 * - Manrope typography and TextPrimary color
 * - Back navigation affordance when viewing sub-tool screens
 *
 * @param currentRoute The current navigation route string.
 * @param onNavigate Callback to trigger screen navigation.
 * @param onBackClick Optional callback to handle back navigation when in sub-tool screens.
 * @param modifier Optional layout modifier.
 */
@Composable
fun SharedToolsBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isScannerActive = currentRoute == Screen.Scanner.route
    val isCommunityActive = currentRoute == Screen.Community.route
    val isSubToolScreen = isScannerActive || isCommunityActive

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceTint)
            .border(1.dp, LimitlessPrimary)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Optional back-navigation button when inside tool screens
            if (isSubToolScreen && onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Back to Persona Home" }
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 1. Scan Accessibility Action Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        color = if (isScannerActive) HighlightBox else SurfaceTint,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = LimitlessPrimary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onNavigate(Screen.Scanner.route) }
                    .semantics { contentDescription = "Scan Accessibility" }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan",
                        style = LimitlessTypography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Community Reports Action Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        color = if (isCommunityActive) HighlightBox else SurfaceTint,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = LimitlessPrimary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onNavigate(Screen.Community.route) }
                    .semantics { contentDescription = "Community Reports" }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Community",
                        style = LimitlessTypography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
