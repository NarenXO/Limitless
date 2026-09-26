package com.teamdexters.limitless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.teamdexters.limitless.assistant.DefaultIntentRouter
import com.teamdexters.limitless.assistant.DefaultWakeWordListener
import com.teamdexters.limitless.assistant.HazelIntent
import com.teamdexters.limitless.assistant.service.HazelAccessibilityService
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
    val context = LocalContext.current
    
    // Hazel state management
    var isHazelListening by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    
    // Initialize wake-word detection pipeline
    DisposableEffect(context) {
        val wakeWordListener = DefaultWakeWordListener(context)
        wakeWordListener.startListening {
            isHazelListening = true
        }
        onDispose {
            wakeWordListener.stopListening()
        }
    }

    // Register BroadcastReceiver for accessibility service action
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == HazelAccessibilityService.ACTION_OPEN_HAZEL) {
                    isHazelListening = true
                }
            }
        }
        val filter = IntentFilter(HazelAccessibilityService.ACTION_OPEN_HAZEL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
    }
    
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
        is HazelIntent.OpenScanner -> {
            navController.navigate("mobility-home")
            onHandled()
        }
        is HazelIntent.OpenCommunity -> {
            navController.navigate("mobility-home")
            onHandled()
        }
        is HazelIntent.OpenPhraseCards -> {
            navController.navigate("speech-home")
            onHandled()
        }
        is HazelIntent.OpenNavigation -> {
            navController.navigate("mobility-home")
            onHandled()
        }
        is HazelIntent.BlindAssist -> {
            navController.navigate("blind-home")
            onHandled()
        }
        is HazelIntent.DeafAssist -> {
            navController.navigate("deaf-home")
            onHandled()
        }
        is HazelIntent.SpeechAssist -> {
            navController.navigate("speech-home")
            onHandled()
        }
        is HazelIntent.MobilityAssist -> {
            navController.navigate("mobility-home")
            onHandled()
        }
        is HazelIntent.GeneralQuery -> {
            // Unmatched freeform query will be handed to Gemini in Phase 3
            onHandled()
        }
        is HazelIntent.Unknown -> {
            onHandled()
        }
    }
}
