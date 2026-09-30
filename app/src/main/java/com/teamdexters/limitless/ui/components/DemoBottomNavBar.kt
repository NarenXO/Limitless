package com.teamdexters.limitless.ui.components

import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessible
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamdexters.limitless.ui.navigation.Screen
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessPrimary
import com.teamdexters.limitless.ui.theme.LimitlessTypography
import com.teamdexters.limitless.ui.theme.SurfaceTint
import com.teamdexters.limitless.ui.theme.TextPrimary

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
