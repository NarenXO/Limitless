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
import javax.inject.Inject
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var hazelActionDispatcher: com.teamdexters.limitless.hazel.HazelActionDispatcher

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
            android.util.Log.d("LIMITLESS_TRACE", "RECORD_AUDIO permission result: micGranted: $isGranted")
            micGranted.value = isGranted
        }

    @Inject
    lateinit var voiceManager: com.teamdexters.limitless.core.audio.VoiceManager

    @Inject
    lateinit var secureKeyProvider: com.teamdexters.limitless.config.SecureKeyProvider

    @Inject
    lateinit var mappedRoomDao: com.teamdexters.limitless.data.local.dao.MappedRoomDao

    @Inject
    lateinit var roomConnectionDao: com.teamdexters.limitless.data.local.dao.RoomConnectionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check current permission state before first frame renders
        micGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        // Request on cold start if not yet granted
        if (!micGranted.value) {
            android.util.Log.d("LIMITLESS_TRACE", "Requesting RECORD_AUDIO permission on cold start")
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        val database = LimitlessDatabase.getDatabase(applicationContext)

        setContent {
            LimitlessTheme {
                HazelAssistantWrapper(
                    database = database,
                    micGranted = micGranted.value,
                    voiceManager = voiceManager,
                    secureKeyProvider = secureKeyProvider,
                    mappedRoomDao = mappedRoomDao,
                    roomConnectionDao = roomConnectionDao,
                    hazelActionDispatcher = hazelActionDispatcher
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.d("VoiceManager", "onStop: releasing mic (app going to background)")
        voiceManager.shutdown()
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("VoiceManager", "onResume: requesting WAKEWORD (app returning to foreground)")
        // Only re-request wakeword if permission is still granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            val granted = voiceManager.requestState(
                com.teamdexters.limitless.core.audio.VoiceState.WAKEWORD,
                force = true
            )
            if (!granted) {
                // A transition was in progress — retry after it clears
                android.util.Log.d("VoiceManager", "onResume: transition in progress, scheduling deferred WAKEWORD retry")
                CoroutineScope(Dispatchers.Main).launch {
                    delay(600L)
                    voiceManager.requestState(
                        com.teamdexters.limitless.core.audio.VoiceState.WAKEWORD,
                        force = true
                    )
                }
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
    micGranted: Boolean,
    voiceManager: com.teamdexters.limitless.core.audio.VoiceManager,
    secureKeyProvider: com.teamdexters.limitless.config.SecureKeyProvider,
    mappedRoomDao: com.teamdexters.limitless.data.local.dao.MappedRoomDao,
    roomConnectionDao: com.teamdexters.limitless.data.local.dao.RoomConnectionDao,
    hazelActionDispatcher: com.teamdexters.limitless.hazel.HazelActionDispatcher
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
    LaunchedEffect(networkStatus) {
        android.util.Log.d("LIMITLESS_TRACE", "Network status changed: ${if(networkStatus is com.teamdexters.limitless.util.NetworkStatus.Online) "ONLINE" else "OFFLINE"}")
    }

    // Hazel query handler with network status tracker integration
    val queryHandler = remember(networkStatusTracker) {
        val geminiClient = com.teamdexters.limitless.assistant.cloud.GeminiClient(secureKeyProvider)
        HazelQueryHandler(
            context = context,
            geminiClient = geminiClient,
            networkStatusTracker = networkStatusTracker
        )
    }

    // Hazel state management
    var isHazelListening by remember { mutableStateOf(false) }
    var responseBannerText by remember { mutableStateOf("") }
    var isBannerVisible by remember { mutableStateOf(false) }
    var waitForTTSCompletion by remember { mutableStateOf(false) }

    // TTS initialization — wired to VoiceManager so mic pauses during TTS
    var ttsRef by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.US
                ttsInstance?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        android.util.Log.d("VoiceManager", "=== TTS_START ===")
                        android.util.Log.d("VoiceManager", "TTS: utteranceId=$utteranceId")
                        voiceManager.onTTSStarted()
                    }
                    override fun onDone(utteranceId: String?) {
                        android.util.Log.d("VoiceManager", "=== TTS_END ===")
                        android.util.Log.d("VoiceManager", "TTS: utteranceId=$utteranceId")
                        voiceManager.onTTSFinished()
                        // VoiceManager handles state transition to ASSISTANT_RECORDING for continuous conversation
                        // Overlay stays visible based on VoiceManager state
                        android.util.Log.d("LIMITLESS_TRACE", "TTS completion - VoiceManager will return to ASSISTANT_RECORDING")
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        android.util.Log.w("VoiceManager", "=== TTS_END (error) ===")
                        android.util.Log.d("VoiceManager", "TTS: utteranceId=$utteranceId")
                        voiceManager.onTTSFinished()
                        // VoiceManager handles state transition even on error
                        android.util.Log.d("LIMITLESS_TRACE", "TTS error - VoiceManager will return to ASSISTANT_RECORDING")
                    }
                })
                ttsRef = ttsInstance
            }
        }
        onDispose {
            ttsInstance?.stop()
            ttsInstance?.shutdown()
            ttsRef = null
        }
    }

    LaunchedEffect(currentRoute, isHazelListening) {
        if (currentRoute in personaHomeRoutes && !isHazelListening) {
            delay(500L)
            voiceManager.requestState(com.teamdexters.limitless.core.audio.VoiceState.WAKEWORD)
        } else {
            if (voiceManager.state.value == com.teamdexters.limitless.core.audio.VoiceState.WAKEWORD) {
                voiceManager.requestState(com.teamdexters.limitless.core.audio.VoiceState.IDLE)
            }
        }
    }

    LaunchedEffect(Unit) {
        voiceManager.state.collect { state ->
            when (state) {
                com.teamdexters.limitless.core.audio.VoiceState.VOICE_ASSISTANT,
                com.teamdexters.limitless.core.audio.VoiceState.ASSISTANT_RECORDING,
                com.teamdexters.limitless.core.audio.VoiceState.ASSISTANT_PROCESSING,
                com.teamdexters.limitless.core.audio.VoiceState.TTS_PLAYING -> {
                    isHazelListening = true
                }
                else -> {
                    isHazelListening = false
                }
            }
        }
    }

    // Register BroadcastReceiver for accessibility service action
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == HazelAccessibilityService.ACTION_OPEN_HAZEL) {
                    android.util.Log.d("LIMITLESS_TRACE", "Physical Volume shortcut triggered (ACTION_OPEN_HAZEL)")
                    // Request ASSISTANT_RECORDING state - VoiceManager controls the transition
                    voiceManager.requestState(com.teamdexters.limitless.core.audio.VoiceState.ASSISTANT_RECORDING, force = true)
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
                            android.util.Log.d("LIMITLESS_TRACE", "Hazel floating mic FAB tapped")
                            // If TTS is playing, interrupt it and start recording
                            if (voiceManager.state.value == com.teamdexters.limitless.core.audio.VoiceState.TTS_PLAYING) {
                                android.util.Log.d("LIMITLESS_TRACE", "Interrupting TTS for user request")
                                voiceManager.interruptTTS()
                            } else {
                                // Request ASSISTANT_RECORDING state - VoiceManager controls the transition
                                voiceManager.requestState(com.teamdexters.limitless.core.audio.VoiceState.ASSISTANT_RECORDING, force = true)
                            }
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
                    intentRouter = intentRouter,
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
                            onHandled = {},
                            hazelActionDispatcher = hazelActionDispatcher
                        )
                    }
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
    onHandled: () -> Unit,
    hazelActionDispatcher: com.teamdexters.limitless.hazel.HazelActionDispatcher
) {
    android.util.Log.d("LIMITLESS_TRACE", "=== INTENT_ROUTING_START ===")
    android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ENTRY - Received intent: ${intent.javaClass.simpleName}")
    
    fun speak(text: String) {
        android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: TTS - Speaking: '$text'")
        tts?.language = java.util.Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "intent_feedback")
    }

    val intentName = intent.javaClass.simpleName
    android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Intent type: $intentName")
    
    val targetRoute = when (intent) {
        is HazelIntent.NavigateTo -> intent.route
        is HazelIntent.OpenScanner -> Screen.Scanner.route
        is HazelIntent.OpenCommunity -> Screen.Community.route
        is HazelIntent.OpenPhraseCards -> Screen.SpeechHome.route
        is HazelIntent.OpenNavigation -> Screen.MobilityHome.route
        is HazelIntent.BlindAssist -> Screen.BlindHome.route
        is HazelIntent.DeafAssist -> Screen.DeafHome.route
        is HazelIntent.SpeechAssist -> Screen.SpeechHome.route
        is HazelIntent.MobilityAssist -> Screen.MobilityHome.route
        else -> "dynamic_query"
    }
    android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Target route: $targetRoute")

    when (intent) {
        is HazelIntent.NavigateTo    -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - NavigateTo")
            when (intent.route) {
                Screen.BlindHome.route -> speak("Navigating to Blind and Low Vision mode for you.")
                Screen.DeafHome.route -> speak("Navigating to Deaf and Hard of Hearing mode for you.")
                Screen.SpeechHome.route -> speak("Navigating to Speech Impaired mode for you.")
                Screen.MobilityHome.route -> speak("Navigating to Mobility and Wheelchair mode for you.")
                Screen.PersonaSelect.route -> speak("Navigating to the main menu for you.")
            }
            navController.navigate(intent.route)
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - NavigateTo completed")
            onHandled()
        }
        is HazelIntent.OpenScanner   -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - OpenScanner")
            speak("Opening Accessibility Scanner for you."); 
            navController.navigate(Screen.Scanner.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - OpenScanner completed")
            onHandled() 
        }
        is HazelIntent.OpenCommunity -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - OpenCommunity")
            speak("Opening Community Reports for you."); 
            navController.navigate(Screen.Community.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - OpenCommunity completed")
            onHandled() 
        }
        is HazelIntent.OpenPhraseCards -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - OpenPhraseCards")
            speak("Navigating to Speech Impaired mode for you."); 
            navController.navigate(Screen.SpeechHome.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - OpenPhraseCards completed")
            onHandled() 
        }
        is HazelIntent.OpenNavigation  -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - OpenNavigation")
            speak("Navigating to Mobility and Wheelchair mode for you."); 
            navController.navigate(Screen.MobilityHome.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - OpenNavigation completed")
            onHandled() 
        }
        is HazelIntent.BlindAssist   -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - BlindAssist")
            speak("Navigating to Blind and Low Vision mode for you."); 
            navController.navigate(Screen.BlindHome.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - BlindAssist completed")
            onHandled() 
        }
        is HazelIntent.DeafAssist    -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - DeafAssist")
            speak("Navigating to Deaf and Hard of Hearing mode for you."); 
            navController.navigate(Screen.DeafHome.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - DeafAssist completed")
            onHandled() 
        }
        is HazelIntent.SpeechAssist  -> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - SpeechAssist")
            speak("Navigating to Speech Impaired mode for you."); 
            navController.navigate(Screen.SpeechHome.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - SpeechAssist completed")
            onHandled() 
        }
        is HazelIntent.MobilityAssist-> { 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - MobilityAssist")
            speak("Navigating to Mobility and Wheelchair mode for you."); 
            navController.navigate(Screen.MobilityHome.route); 
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - MobilityAssist completed")
            onHandled() 
        }
        is HazelIntent.GeneralQuery  -> {
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - GeneralQuery")
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Raw query: '${intent.rawQuery}'")
            onHandled()
            
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Checking HazelActionDispatcher")
            val handled = hazelActionDispatcher.parseIntent(intent.rawQuery)
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: HazelActionDispatcher result: $handled")
            
            if (!handled) {
                android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Delegating to HazelQueryHandler")
                queryHandler.handleGeneralQuery(
                    rawQuery = intent.rawQuery,
                    scope = scope,
                    tts = tts,
                    onResponseReady = onShowBanner
                )
            } else {
                android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - GeneralQuery handled by HazelActionDispatcher")
            }
        }
        is HazelIntent.VisionQuery  -> {
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - VisionQuery")
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Vision query: '${intent.query}'")
            onHandled()
            queryHandler.handleVisionQuery(
                query = intent.query,
                scope = scope,
                tts = tts,
                onResponseReady = onShowBanner
            )
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - VisionQuery completed")
        }
        is HazelIntent.Unknown -> {
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_START - Unknown")
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Raw query: '${intent.rawQuery}'")
            onHandled()
            
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Checking HazelActionDispatcher")
            val handled = hazelActionDispatcher.parseIntent(intent.rawQuery)
            android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: HazelActionDispatcher result: $handled")
            
            if (!handled) {
                android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: Delegating to HazelQueryHandler")
                queryHandler.handleGeneralQuery(
                    rawQuery = intent.rawQuery,
                    scope = scope,
                    tts = tts,
                    onResponseReady = onShowBanner
                )
            } else {
                android.util.Log.d("LIMITLESS_TRACE", "INTENT_ROUTING: ACTION_SUCCESS - Unknown handled by HazelActionDispatcher")
            }
        }
    }
    android.util.Log.d("LIMITLESS_TRACE", "=== INTENT_ROUTING_STOP ===")
}
