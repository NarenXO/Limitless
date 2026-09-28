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
        android.util.Log.e("NAV_DEBUG", "navigateBackToPersonaSelect triggered! Current route: ${navController.currentBackStackEntry?.destination?.route}")
        try {
            // Attempt normal pop first
            val popped = navController.popBackStack()
            android.util.Log.e("NAV_DEBUG", "popBackStack result: $popped")
            
            // If not popped or still on same screen, force navigate to PersonaSelect
            if (!popped || navController.currentBackStackEntry?.destination?.route != Screen.PersonaSelect.route) {
                navController.navigate(Screen.PersonaSelect.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NAV_DEBUG", "Error navigating back, forcing navigate", e)
            navController.navigate(Screen.PersonaSelect.route) {
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