package com.teamdexters.limitless.ui.screens

import androidx.compose.runtime.Composable
import com.teamdexters.limitless.ui.mobility.MobilityHomeScreen as FullMobilityHomeScreen

/**
 * Mobility Home Screen entry delegate.
 * Delegates to com.teamdexters.limitless.ui.mobility.MobilityHomeScreen.
 */
@Composable
fun MobilityHomeScreen(
    onBack: () -> Unit = {}
) {
    FullMobilityHomeScreen(onBack = onBack)
}