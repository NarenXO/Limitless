package com.teamdexters.limitless.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.ui.persona.PersonaSelectScreen
import com.teamdexters.limitless.ui.screens.BlindHomeScreen
import com.teamdexters.limitless.ui.screens.CommunityScreen
import com.teamdexters.limitless.ui.screens.DeafHomeScreen
import com.teamdexters.limitless.ui.screens.MobilityHomeScreen
import com.teamdexters.limitless.ui.screens.ScannerScreen
import com.teamdexters.limitless.ui.speech.SpeechHomeScreen

/**
 * Navigation host for the Limitless application.
 * Defines all navigation routes and their corresponding screens.
 */
@Composable
fun LimitlessNavHost(
    navController: NavHostController,
    database: LimitlessDatabase
) {
    val navigateBackToPersonaSelect: () -> Unit = {
        val popped = navController.popBackStack(Screen.PersonaSelect.route, inclusive = false)
        if (!popped) {
            navController.navigate(Screen.PersonaSelect.route) {
                popUpTo(Screen.PersonaSelect.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.PersonaSelect.route
    ) {
        // Persona Selection Screen
        composable(Screen.PersonaSelect.route) {
            PersonaSelectScreen(
                navController = navController,
                database = database
            )
        }

        // Blind Home Screen
        composable(Screen.BlindHome.route) {
            BlindHomeScreen(onBack = navigateBackToPersonaSelect)
        }
        
        // Deaf Home Screen
        composable(Screen.DeafHome.route) {
            DeafHomeScreen(onBack = navigateBackToPersonaSelect)
        }
        
        // Speech Home Screen
        composable(Screen.SpeechHome.route) {
            SpeechHomeScreen(onBack = navigateBackToPersonaSelect)
        }
        
        // Mobility Home Screen
        composable(Screen.MobilityHome.route) {
            com.teamdexters.limitless.ui.mobility.MobilityHomeScreen(onBack = navigateBackToPersonaSelect)
        }

        // Accessibility Scanner Screen
        composable(Screen.Scanner.route) {
            ScannerScreen()
        }

        // Community Reports Screen
        composable(Screen.Community.route) {
            CommunityScreen()
        }
    }
}