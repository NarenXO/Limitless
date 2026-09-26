package com.teamdexters.limitless

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.teamdexters.limitless.assistant.DefaultIntentRouter
import com.teamdexters.limitless.assistant.HazelIntent
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.ui.components.HazelFloatingMicButton
import com.teamdexters.limitless.ui.components.HazelListeningOverlay
import com.teamdexters.limitless.ui.navigation.LimitlessNavHost
import com.teamdexters.limitless.ui.theme.LimitlessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize database
        val database = LimitlessDatabase.getDatabase(applicationContext)
        
        setContent {
            LimitlessTheme {
                HazelAssistantWrapper(database)
            }
        }
    }
}

/**
 * Global wrapper for Hazel assistant integration.
 * Wraps the navigation host in a Scaffold with the floating mic button
 * and manages the Hazel listening overlay state.
 */
@Composable
fun HazelAssistantWrapper(database: LimitlessDatabase) {
    val navController = rememberNavController()
    val intentRouter = remember { DefaultIntentRouter() }
    
    // Hazel state management
    var isHazelListening by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            bottomBar = {
                // Floating mic button positioned at bottom right
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    HazelFloatingMicButton(
                        onClick = {
                            isHazelListening = true
                            transcribedText = ""
                            // TODO: Start speech recognition here
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LimitlessNavHost(
                    navController = navController,
                    database = database
                )
                
                // Hazel listening overlay
                HazelListeningOverlay(
                    isVisible = isHazelListening,
                    transcribedText = transcribedText,
                    onDismiss = {
                        isHazelListening = false
                        transcribedText = ""
                        // TODO: Stop speech recognition here
                    },
                    onIntentResult = { intent ->
                        handleHazelIntent(intent, navController) {
                            isHazelListening = false
                            transcribedText = ""
                        }
                    },
                    intentRouter = intentRouter
                )
            }
        }
    }
}

/**
 * Handles Hazel intent results and performs appropriate navigation.
 */
private fun handleHazelIntent(
    intent: HazelIntent,
    navController: NavController,
    onHandled: () -> Unit
) {
    when (intent) {
        is HazelIntent.NavigateTo -> {
            navController.navigate(intent.route)
            onHandled()
        }
        is HazelIntent.BlindAssist -> {
            // TODO: Navigate to blind home with specific action
            navController.navigate("blind-home")
            onHandled()
        }
        is HazelIntent.DeafAssist -> {
            // TODO: Navigate to deaf home with specific action
            navController.navigate("deaf-home")
            onHandled()
        }
        is HazelIntent.SpeechAssist -> {
            // TODO: Navigate to speech home with specific action
            navController.navigate("speech-home")
            onHandled()
        }
        is HazelIntent.MobilityAssist -> {
            // TODO: Navigate to mobility home with specific action
            navController.navigate("mobility-home")
            onHandled()
        }
        is HazelIntent.Unknown -> {
            // TODO: Handle unknown intent (optional Gemini cloud boost)
            // For now, just close the overlay
            onHandled()
        }
    }
}
