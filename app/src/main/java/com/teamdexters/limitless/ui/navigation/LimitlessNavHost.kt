package com.teamdexters.limitless.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.ui.persona.PersonaSelectScreen
import com.teamdexters.limitless.ui.screens.BlindHomeScreen
import com.teamdexters.limitless.ui.screens.CommunityScreen
import com.teamdexters.limitless.ui.screens.DeafHomeScreen
import com.teamdexters.limitless.ui.scanner.LocationDetailScreen
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

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.teamdexters.limitless.util.PowerTriggerBus.triggerEvent.collect {
            android.util.Log.d("LIMITLESS_TRACE", "LimitlessNavHost: PowerTriggerBus event received")

            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != "scanner") {
                navController.navigate("scanner") {
                    launchSingleTop = true
                }
            }

            // Vibrate 200ms
            val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
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
            BlindHomeScreen()
        }
        
        // Deaf Home Screen
        composable(Screen.DeafHome.route) {
            DeafHomeScreen()
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
            ScannerScreen(navController = navController)
        }

        // Community Reports Screen
        composable(Screen.Community.route) {
            CommunityScreen(navController = navController)
        }

        // Location Detail Screen
        composable(
            route = Screen.LocationDetail.route,
            arguments = listOf(navArgument("locationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getLong("locationId") ?: return@composable
            LocationDetailScreen(
                locationId = locationId,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Mapping Home
        composable("mapping-home") {
            com.teamdexters.limitless.ui.roommapping.MappingHomeScreen(
                mappedRoomDao = database.mappedRoomDao(),
                roomConnectionDao = database.roomConnectionDao(),
                onMapNewRoom = { navController.navigate("qr-scan") },
                onOpenRoom = { roomId -> navController.navigate("room-connection/$roomId") }
            )
        }
        
        // QR Scan
        composable("qr-scan") {
            com.teamdexters.limitless.ui.roommapping.QRScanScreen(
                onRoomDetected = { roomId -> navController.navigate("room-capture/$roomId") }
            )
        }
        
        // Room Capture
        composable(
            route = "room-capture/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            com.teamdexters.limitless.ui.roommapping.RoomCaptureScreen(
                roomId = roomId,
                onAnalyzeRoom = { savedPaths, rId -> 
                    navController.navigate("room-analysis/$rId")
                }
            )
        }
        
        // Room Analysis
        composable(
            route = "room-analysis/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            
            val context = androidx.compose.ui.platform.LocalContext.current
            val baseDir = java.io.File(context.filesDir, "mapped_rooms/$roomId")
            val photoPaths = listOf(
                java.io.File(baseDir, "photo_0.jpg").absolutePath,
                java.io.File(baseDir, "photo_1.jpg").absolutePath,
                java.io.File(baseDir, "photo_2.jpg").absolutePath,
                java.io.File(baseDir, "photo_3.jpg").absolutePath
            ).filter { java.io.File(it).exists() }
            
            com.teamdexters.limitless.ui.roommapping.RoomAnalysisScreen(
                roomId = roomId,
                photoPaths = photoPaths,
                mappedRoomDao = database.mappedRoomDao(),
                onRoomSaved = { navController.navigate("photo-route/$roomId") } // Handoff to PhotoRouteScreen!
            )
        }

        // Photo Route (Spatial Route Generator)
        composable(
            route = "photo-route/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val context = androidx.compose.ui.platform.LocalContext.current
            val roomDir = java.io.File(context.filesDir, "mapped_rooms/$roomId")
            val photoPaths = (0..3).map { index -> java.io.File(roomDir, "photo_$index.jpg").absolutePath }
                .filter { java.io.File(it).exists() }

            com.teamdexters.limitless.ui.roommapping.PhotoRouteScreen(
                roomId = roomId,
                photoPaths = photoPaths,
                onRouteSaved = {
                    navController.navigate("room-connection/$roomId")
                }
            )
        }
        
        // Room Connection
        composable(
            route = "room-connection/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            com.teamdexters.limitless.ui.roommapping.RoomConnectionScreen(
                currentRoomId = roomId,
                mappedRoomDao = database.mappedRoomDao(),
                roomConnectionDao = database.roomConnectionDao(),
                onMapAnotherRoom = { navController.navigate("qr-scan") },
                onDoneMapping = { navController.navigate("mapping-home") }
            )
        }
    }
}
