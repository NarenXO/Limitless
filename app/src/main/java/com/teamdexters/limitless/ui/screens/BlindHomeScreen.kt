package com.teamdexters.limitless.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.teamdexters.limitless.ui.blind.BlindAssistScreen

@Composable
fun BlindHomeScreen(navController: NavHostController) {
    BlindAssistScreen(navController = navController)
}
