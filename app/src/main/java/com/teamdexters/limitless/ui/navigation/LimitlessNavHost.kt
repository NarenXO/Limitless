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
import com.teamdexters.limitless.ui.screens.SpeechHomeScreen

/**
 * Navigation host for the Limitless application.
 * Defines all navigation routes and their corresponding screens.
 */
@Composable
fun LimitlessNavHost(
    navController: NavHostController,
    database: LimitlessDatabase?
) {
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
            BlindHomeScreen()
        }
        
        // Deaf Home Screen
        composable(Screen.DeafHome.route) {
            DeafHomeScreen()
        }
        
        // Speech Home Screen
        composable(Screen.SpeechHome.route) {
            SpeechHomeScreen()
        }
        
        // Mobility Home Screen
        composable(Screen.MobilityHome.route) {
            MobilityHomeScreen()
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