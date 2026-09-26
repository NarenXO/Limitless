package com.teamdexters.limitless.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.teamdexters.limitless.ui.theme.LimitlessBackground
import com.teamdexters.limitless.ui.theme.TextPrimary

/**
 * Placeholder home screen for mobility and wheelchair users.
 * This screen will be expanded with accessibility features in later phases.
 */
@Composable
fun MobilityHomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Mobility & Wheelchair Navigation",
            color = TextPrimary
        )
    }
}