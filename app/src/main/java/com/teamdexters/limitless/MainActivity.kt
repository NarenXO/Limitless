package com.teamdexters.limitless

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teamdexters.limitless.assistant.DefaultIntentRouter
import com.teamdexters.limitless.assistant.DefaultWakeWordListener
import com.teamdexters.limitless.assistant.HazelIntent
import com.teamdexters.limitless.assistant.HazelQueryHandler
import com.teamdexters.limitless.assistant.service.HazelAccessibilityService
import com.teamdexters.limitless.data.local.LimitlessDatabase
import com.teamdexters.limitless.ui.components.HazelFloatingMicButton
import com.teamdexters.limitless.ui.components.HazelListeningOverlay
import com.teamdexters.limitless.ui.components.HazelResponseBanner
import com.teamdexters.limitless.ui.components.NetworkStatusBadge
import com.teamdexters.limitless.ui.components.SharedToolsBar
import com.teamdexters.limitless.ui.navigation.LimitlessNavHost
import com.teamdexters.limitless.ui.navigation.Screen
import com.teamdexters.limitless.ui.theme.LimitlessTheme
import com.teamdexters.limitless.util.NetworkStatusTracker
import kotlinx.coroutines.CoroutineScope
import java.util.Locale

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
 * Wraps the navigation host in a Scaffold with the shared quick tools bar,
 * floating mic button, Hazel listening overlay, response banner, network status badge,
 * and offline/online query fallback.
 *
 * Integrates [NetworkStatusTracker] for reactive app-wide network monitoring.
 * The tracker is registered on composition and unregistered on disposal to prevent memory leaks.
 */
@Composable
fun HazelAssistantWrapper(database: LimitlessDatabase) {
    val navController = rememberNavController()
    val intentRouter = remember { DefaultIntentRouter() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Track current navigation route
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val personaHomeRoutes = listOf(
        Screen.BlindHome.route,
        Screen.DeafHome.route,
        Screen.SpeechHome.route,
        Screen.MobilityHome.route,
        Screen.Scanner.route,
        Screen.Community.route
    )
    val showToolsBar = currentRoute in personaHomeRoutes

    // App-wide network status tracker (register/unregister lifecycle-safe)
    val networkStatusTracker = remember { NetworkStatusTracker(context) }
    DisposableEffect(networkStatusTracker) {
        networkStatusTracker.register()
        onDispose {
            networkStatusTracker.unregister()
        }
    }
    val networkStatus by networkStatusTracker.statusFlow.collectAsState()

    // Hazel query handler with network status tracker integration
    val queryHandler = remember(networkStatusTracker) {
        HazelQueryHandler(
            context = context,
            networkStatusTracker = networkStatusTracker
        )
    }
    
    // TTS initialization
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsRef?.language = Locale.US
            }
        }
        ttsRef = tts
        onDispose {
            tts.stop()
            tts.shutdown()
            ttsRef = null
        }
    }
    
    // Hazel state management
    var isHazelListening by remember { mutableStateOf(false) }
    var transcribedText by remember { mutableStateOf("") }
    var responseBannerText by remember { mutableStateOf("") }
    var isBannerVisible by remember { mutableStateOf(false) }
    
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
                if (showToolsBar) {
                    SharedToolsBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onBackClick = {
                            navController.popBackStack()
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

                // Network status badge: top-right corner, unobtrusive
                NetworkStatusBadge(
                    networkStatus = networkStatus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )

                // Floating mic button anchored to bottom right of content screen
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
                        }
                    )
                }
                
                // Accessible subtitle response banner (fires simultaneous TTS + visual subtitle)
                HazelResponseBanner(
                    text = responseBannerText,
                    isVisible = isBannerVisible,
                    onDismiss = {
                        isBannerVisible = false
                    },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                
                // Hazel listening overlay
                HazelListeningOverlay(
                    isVisible = isHazelListening,
                    transcribedText = transcribedText,
                    onDismiss = {
                        isHazelListening = false
                        transcribedText = ""
                    },
                    onIntentResult = { intent ->
                        handleHazelIntent(
                            intent = intent,
                            navController = navController,
                            queryHandler = queryHandler,
                            scope = coroutineScope,
                            tts = ttsRef,
                            onShowBanner = { text ->
                                responseBannerText = text
                                isBannerVisible = true
                            },
                            onHandled = {
                                isHazelListening = false
                                transcribedText = ""
                            }
                        )
                    },
                    intentRouter = intentRouter
                )
            }
        }
    }
}

/**
 * Handles Hazel intent results, performs appropriate navigation or triggers GeneralQuery fallback.
 */
private fun handleHazelIntent(
    intent: HazelIntent,
    navController: NavController,
    queryHandler: HazelQueryHandler,
    scope: CoroutineScope,
    tts: TextToSpeech?,
    onShowBanner: (String) -> Unit,
    onHandled: () -> Unit
) {
    when (intent) {
        is HazelIntent.NavigateTo -> {
            navController.navigate(intent.route)
            onHandled()
        }
        is HazelIntent.OpenScanner -> {
            navController.navigate(Screen.Scanner.route)
            onHandled()
        }
        is HazelIntent.OpenCommunity -> {
            navController.navigate(Screen.Community.route)
            onHandled()
        }
        is HazelIntent.OpenPhraseCards -> {
            navController.navigate(Screen.SpeechHome.route)
            onHandled()
        }
        is HazelIntent.OpenNavigation -> {
            navController.navigate(Screen.MobilityHome.route)
            onHandled()
        }
        is HazelIntent.BlindAssist -> {
            navController.navigate(Screen.BlindHome.route)
            onHandled()
        }
        is HazelIntent.DeafAssist -> {
            navController.navigate(Screen.DeafHome.route)
            onHandled()
        }
        is HazelIntent.SpeechAssist -> {
            navController.navigate(Screen.SpeechHome.route)
            onHandled()
        }
        is HazelIntent.MobilityAssist -> {
            navController.navigate(Screen.MobilityHome.route)
            onHandled()
        }
        is HazelIntent.GeneralQuery -> {
            onHandled()
            queryHandler.handleGeneralQuery(
                rawQuery = intent.rawQuery,
                scope = scope,
                tts = tts,
                onResponseReady = onShowBanner
            )
        }
        is HazelIntent.Unknown -> {
            onHandled()
            queryHandler.handleGeneralQuery(
                rawQuery = intent.rawQuery,
                scope = scope,
                tts = tts,
                onResponseReady = onShowBanner
            )
        }
    }
}
