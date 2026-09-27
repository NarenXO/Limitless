package com.teamdexters.limitless.ui.screens

import androidx.compose.runtime.Composable
import com.teamdexters.limitless.ui.scanner.AccessibilityScannerScreen

/**
 * Accessibility Scanner screen – delegates to the full Phase 4 implementation.
 * ScannerViewModel is instantiated via hiltViewModel() inside AccessibilityScannerScreen.
 */
@Composable
fun ScannerScreen(navController: androidx.navigation.NavHostController) {
    AccessibilityScannerScreen(navController = navController)
}
