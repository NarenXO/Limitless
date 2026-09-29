package com.teamdexters.limitless

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.teamdexters.limitless.ui.components.DemoBottomNavBar
import com.teamdexters.limitless.ui.components.DemoTopAppBar
import com.teamdexters.limitless.ui.navigation.LimitlessNavHost
import com.teamdexters.limitless.ui.navigation.Screen
import com.teamdexters.limitless.ui.theme.HighlightBox
import com.teamdexters.limitless.ui.theme.LimitlessTheme
import com.teamdexters.limitless.ui.theme.TextPrimary
import com.teamdexters.limitless.util.NetworkStatusTracker
import kotlinx.coroutines.CoroutineScope
import java.util.Locale

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Tracks whether the user has granted RECORD_AUDIO at runtime.
     * Initialized eagerly so the Compose tree always reads the correct value.
     */
    private val micGranted = mutableStateOf(false)

    /**
     * Runtime permission launcher for RECORD_AUDIO.
     * Must be registered before onCreate returns (ActivityResultContracts).
     */
    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            micGranted.value = isGranted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check current permission state before first frame renders
        micGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Request on cold start if not yet granted
        if (!micGranted.value) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        val database = LimitlessDatabase.getDatabase(applicationContext)

        setContent {
            LimitlessTheme {
                HazelAssistantWrapper(
                    database = database,
                    micGranted = micGranted.value
                )
            }
        }
    }
}

/**
 * Global wrapper for Hazel assistant integration.
 *
 * When [micGranted] is false, a subtle denial chip is shown at the top of the screen
 * informing the user that the microphone is required for voice commands. All other
 * features remain fully functional.
 *
 * Integrates [NetworkStatusTracker] for reactive app-wide network monitoring.
 */
@Composable
fun HazelAssistantWrapper(
    database: LimitlessDatabase,
    micGranted: Boolean
) {
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

    // Initialize wake-word detection pipeline (only when mic is granted)
    DisposableEffect(context, micGranted) {
        if (!micGranted) return@DisposableEffect onDispose {}
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
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (showToolsBar) {
                    DemoTopAppBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        },
                        modifier = Modifier.statusBarsPadding().padding(top = 12.dp)
                    )
                }
            },
            bottomBar = {
                if (showToolsBar) {
                    DemoBottomNavBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (currentRoute != route) {
                                navController.navigate(route) { launchSingleTop = true }
                            }
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

                // ── Network status badge: top-right corner, unobtrusive ──────
                NetworkStatusBadge(
                    networkStatus = networkStatus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                )

                // ── Mic-denied chip: subtle top-left notice ──────────────────
                MicDeniedChip(
                    visible = !micGranted,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp)
                )

                // ── Floating mic button ──────────────────────────────────────
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

                // ── Response banner ──────────────────────────────────────────
                HazelResponseBanner(
                    text = responseBannerText,
                    isVisible = isBannerVisible,
                    onDismiss = { isBannerVisible = false },
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                // ── Hazel listening overlay ──────────────────────────────────
                HazelListeningOverlay(
                    isVisible = isHazelListening,
                    onDismiss = {
                        isHazelListening = false
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
 * Subtle chip shown when RECORD_AUDIO permission is denied.
 * Uses HighlightBox background and MicOff icon — no emoji, no white (#FFFFFF).
 * Full TalkBack content description for accessibility.
 */
@Composable
private fun MicDeniedChip(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .background(HighlightBox, RoundedCornerShape(20.dp))
                .border(1.dp, TextPrimary.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics {
                    contentDescription =
                        "Microphone permission denied. Voice commands are unavailable. " +
                        "Please enable the microphone in Settings."
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MicOff,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.padding(end = 2.dp)
            )
            Text(
                text = "Mic off — voice commands unavailable",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

/**
 * Handles Hazel intent results — navigation or GeneralQuery cloud fallback.
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
    fun speak(text: String) {
        tts?.language = java.util.Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "intent_feedback")
    }

    when (intent) {
        is HazelIntent.NavigateTo    -> { 
            when (intent.route) {
                Screen.BlindHome.route -> speak("Switching to Blind and Low Vision mode")
                Screen.DeafHome.route -> speak("Switching to Deaf and Hard of Hearing mode")
                Screen.SpeechHome.route -> speak("Switching to Speech Impaired mode")
                Screen.MobilityHome.route -> speak("Switching to Mobility and Wheelchair mode")
                Screen.PersonaSelect.route -> speak("Opening main menu")
            }
            navController.navigate(intent.route)
            onHandled()
        }
        is HazelIntent.OpenScanner   -> { speak("Opening Accessibility Scanner"); navController.navigate(Screen.Scanner.route); onHandled() }
        is HazelIntent.OpenCommunity -> { speak("Opening Community Reports"); navController.navigate(Screen.Community.route); onHandled() }
        is HazelIntent.OpenPhraseCards -> { speak("Switching to Speech Impaired mode"); navController.navigate(Screen.SpeechHome.route); onHandled() }
        is HazelIntent.OpenNavigation  -> { speak("Switching to Mobility and Wheelchair mode"); navController.navigate(Screen.MobilityHome.route); onHandled() }
        is HazelIntent.BlindAssist   -> { speak("Switching to Blind and Low Vision mode"); navController.navigate(Screen.BlindHome.route); onHandled() }
        is HazelIntent.DeafAssist    -> { speak("Switching to Deaf and Hard of Hearing mode"); navController.navigate(Screen.DeafHome.route); onHandled() }
        is HazelIntent.SpeechAssist  -> { speak("Switching to Speech Impaired mode"); navController.navigate(Screen.SpeechHome.route); onHandled() }
        is HazelIntent.MobilityAssist-> { speak("Switching to Mobility and Wheelchair mode"); navController.navigate(Screen.MobilityHome.route); onHandled() }
        is HazelIntent.GeneralQuery  -> {
            onHandled()
            queryHandler.handleGeneralQuery(
                rawQuery = intent.rawQuery,
                scope = scope,
                tts = tts,
                onResponseReady = onShowBanner
            )
        }
        is HazelIntent.VisionQuery  -> {
            onHandled()
            queryHandler.handleVisionQuery(
                query = intent.query,
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
