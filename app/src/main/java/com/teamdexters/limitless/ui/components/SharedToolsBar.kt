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
import androidx.compose.material.icons.filled.Map
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

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults

/**
 * Top App Bar containing the "Scan" and "Community" tools.
 */
@Composable
fun DemoTopAppBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isScannerActive = currentRoute == Screen.Scanner.route
    val isCommunityActive = currentRoute == Screen.Community.route

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceTint)
            .border(1.dp, LimitlessPrimary)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
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
        Spacer(modifier = Modifier.width(12.dp))

        // 3. Map Rooms Action Button
        val isMappingActive = currentRoute == "mapping-home"
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(
                    color = if (isMappingActive) HighlightBox else SurfaceTint,
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = LimitlessPrimary,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { onNavigate("mapping-home") }
                .semantics { contentDescription = "Map Rooms" }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Map",
                    style = LimitlessTypography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
            }
        }
    }
}

/**
 * Bottom Nav Bar for fast switching between persona homes.
 */
@Composable
fun DemoBottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.border(1.dp, LimitlessPrimary),
        containerColor = SurfaceTint,
        contentColor = TextPrimary
    ) {
        val tabs = listOf(
            Triple(Screen.BlindHome.route, "Blind", Icons.Default.Visibility),
            Triple(Screen.DeafHome.route, "Deaf", Icons.Default.Hearing),
            Triple(Screen.SpeechHome.route, "Speech", Icons.Default.QuestionAnswer),
            Triple(Screen.MobilityHome.route, "Mobility", Icons.Default.Accessible)
        )

        tabs.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, style = LimitlessTypography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TextPrimary,
                    selectedTextColor = TextPrimary,
                    indicatorColor = HighlightBox,
                    unselectedIconColor = TextPrimary.copy(alpha = 0.6f),
                    unselectedTextColor = TextPrimary.copy(alpha = 0.6f)
                )
            )
        }
    }
}
