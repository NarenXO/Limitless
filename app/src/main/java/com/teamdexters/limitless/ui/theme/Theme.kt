package com.teamdexters.limitless.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LimitlessColorScheme = lightColorScheme(
    primary = LimitlessPrimary,
    background = PureWhite,
    surface = SoftWhite,
    onPrimary = PureWhite,
    onBackground = PureBlack,
    onSurface = PureBlack
)

@Composable
fun LimitlessTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = LimitlessColorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = LimitlessColorScheme,
        typography = LimitlessTypography,
        content = content
    )
}
