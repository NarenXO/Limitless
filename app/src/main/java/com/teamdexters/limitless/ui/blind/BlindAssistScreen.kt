package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.teamdexters.limitless.blind.BlindAIInvoker
import com.teamdexters.limitless.blind.BlindCameraController
import com.teamdexters.limitless.blind.BlindNavigationVoice
import com.teamdexters.limitless.blind.CameraFrameManager
import com.teamdexters.limitless.blind.OfflineObjectDetector
import com.teamdexters.limitless.blind.OfflineTextReader
import com.teamdexters.limitless.ui.components.HazelResponseBanner
import com.teamdexters.limitless.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blind Assist Screen with CameraX PreviewView.
 * Features 1 FPS camera duty cycling, proximity sensor gating, and on-demand AI invocation.
 * Full-screen camera with translucent top bar, result banner, bottom action bar, and floating mic button.
 */
@Composable
fun BlindAssistScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera permission — mutableStateOf so permission grant triggers recomposition
    // and the AndroidView.update block re-evaluates cameraStarted.
    var cameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // TTS manager
    val ttsManager = remember { TTSManager(context) }

    // Camera controller
    val cameraController = remember { BlindCameraController(context) }
    var isCameraPaused by remember { mutableStateOf(false) }
    var cameraStatus by remember { mutableStateOf("Camera Active") }
    // Guard: prevent re-binding CameraX on every recomposition
    var cameraStarted by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        cameraPermission = isGranted
        if (!isGranted) {
            Log.w("LIMITLESS_TRACE", "BlindAssistScreen: Camera permission denied")
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
            // Speak TTS so blind users understand why features are unavailable
            ttsManager.speak("Camera permission needed for vision features.")
        } else {
            Log.d("LIMITLESS_TRACE", "BlindAssistScreen: Camera permission granted")
            // Reset binding guard so the AndroidView.update block can now bind the camera
            cameraStarted = false
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission) {
            Log.d("LIMITLESS_TRACE", "BlindAssistScreen: Requesting CAMERA permission")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // AI invocation state
    var aiResponse by remember { mutableStateOf("") }
    var showResponseBanner by remember { mutableStateOf(false) }
    var isProcessingAI by remember { mutableStateOf(false) }
    var testQueryIndex by remember { mutableIntStateOf(0) }

    // Navigation state
    var showNavigationOverlay by remember { mutableStateOf(false) }

    // Test queries for offline fallback testing
    val testQueries = listOf(
        "describe surroundings",
        "what's in front",
        "read text",
        "detect obstacles",
        "what color"
    )

    // Initialize camera controller and AI invoker
    LaunchedEffect(Unit) {
        cameraController.initialize()
        BlindAIInvoker.initialize(context)
        BlindNavigationVoice.initialize(context)
        ttsManager.initialize { success ->
            if (!success) {
                Toast.makeText(context, "Text-to-speech initialization failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Monitor camera pause state from proximity sensor
    LaunchedEffect(Unit) {
        while (true) {
            delay(200)
            isCameraPaused = cameraController.isPaused()
            cameraStatus = if (isCameraPaused) "Camera Paused" else "Camera Active"
            // BlindCameraController logs frame updates via CameraFrameManager
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
            BlindAIInvoker.release()
            BlindNavigationVoice.release()
            ttsManager.release()
        }
    }

    // Trigger haptic feedback
    fun triggerHaptic() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(150)
            }
        }
    }

    // Handle AI invocation with specific query
    fun handleAIInvocation(query: String) {
        if (isProcessingAI) return

        scope.launch {
            isProcessingAI = true
            try {
                val response = BlindAIInvoker.invokeVisionAI(context, query)

                aiResponse = response
                showResponseBanner = true

                // Speak response
                ttsManager.speak(response)

                // Trigger haptic feedback
                triggerHaptic()

                // Auto-hide banner after 8 seconds
                delay(8000)
                showResponseBanner = false
            } catch (e: Exception) {
                Toast.makeText(context, "AI invocation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessingAI = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LimitlessBackground)
    ) {
        if (cameraPermission) {
            // Camera preview (base layer)
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    // Guard: only bind camera once to prevent recomposition-driven unbind/rebind
                    // which would disrupt frame analysis and leave CameraFrameManager without a real frame.
                    if (!cameraStarted) {
                        cameraStarted = true
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            // Preview use case
                            val preview = Preview.Builder()
                                .build()
                                .also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                            // Image analysis use case with 1 FPS duty cycling
                            // BlindCameraController.analyzeFrame() calls CameraFrameManager.updateLatestFrame()
                            // ensuring CameraFrameManager always has a real frame after the first analysis tick.
                            val imageAnalysis = cameraController.createImageAnalysis()

                            // Bind to lifecycle
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                                Log.d("LIMITLESS_TRACE", "BlindAssistScreen: CameraX bound — Preview + ImageAnalysis active, frames flowing to CameraFrameManager")
                            } catch (e: Exception) {
                                Log.e("LIMITLESS_TRACE", "CameraX binding failed", e)
                                // Speak TTS so blind users know the camera failed
                                ttsManager.speak("Unable to access camera hardware.")
                                Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            )

            // Top Bar: Translucent SurfaceTint pill with title + camera status + proximity indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = SurfaceTint.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title
                        Text(
                            text = "Blind Assist",
                            style = LimitlessTypography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.semantics {
                                contentDescription = "Blind Assist screen title"
                            }
                        )

                        // Camera status
                        Text(
                            text = cameraStatus,
                            style = LimitlessTypography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            modifier = Modifier.semantics {
                                contentDescription = "Camera status: $cameraStatus"
                            }
                        )

                        // Proximity pause indicator chip
                        if (isCameraPaused) {
                            Surface(
                                color = HighlightBox,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.semantics {
                                    contentDescription = "Camera paused because proximity sensor is covered"
                                }
                            ) {
                                Text(
                                    text = "Proximity Covered",
                                    style = LimitlessTypography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // AI response banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                HazelResponseBanner(
                    text = aiResponse,
                    isVisible = showResponseBanner && aiResponse.isNotEmpty(),
                    onDismiss = { showResponseBanner = false },
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Floating semi-transparent bottom action bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Surface(
                    color = SurfaceTint.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Describe button
                        Button(
                            onClick = { handleAIInvocation("describe surroundings") },
                            modifier = Modifier
                                .height(72.dp)
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .semantics {
                                    contentDescription = "Describe surroundings button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Describe",
                                style = LimitlessTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        // Read Text Aloud button — OfflineTextReader with grayscale+contrast preprocessing
                        Button(
                            onClick = { 
                                if (!isProcessingAI) {
                                    scope.launch {
                                        isProcessingAI = true
                                        try {
                                            val (frame, rotation) = CameraFrameManager.getLatestFrame()
                                            Log.d("LIMITLESS_TRACE", "BlindAssistScreen: Read Text triggered, frame=${frame.width}x${frame.height}, rotation=$rotation")
                                            val response = OfflineTextReader.readText(frame, rotation)
                                            aiResponse = response
                                            showResponseBanner = true
                                            ttsManager.speak(response)
                                            triggerHaptic()
                                            delay(8000)
                                            showResponseBanner = false
                                        } catch (e: Exception) {
                                            Log.e("LIMITLESS_TRACE", "BlindAssistScreen: Read Text failed", e)
                                            ttsManager.speak("No readable text detected. Move closer and hold steady.")
                                        } finally {
                                            isProcessingAI = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(72.dp)
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .semantics {
                                    contentDescription = "Read text button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Read Text",
                                style = LimitlessTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        // Obstacles button — OfflineObjectDetector.detectObstacles() directly
                        Button(
                            onClick = {
                                if (!isProcessingAI) {
                                    scope.launch {
                                        isProcessingAI = true
                                        try {
                                            val (frame, rotation) = CameraFrameManager.getLatestFrame()
                                            Log.d("LIMITLESS_TRACE", "BlindAssistScreen: Obstacles triggered, frame=${frame.width}x${frame.height}")
                                            val response = OfflineObjectDetector.detectObstacles(frame)
                                            aiResponse = response
                                            showResponseBanner = true
                                            ttsManager.speak(response)
                                            triggerHaptic()
                                            delay(8000)
                                            showResponseBanner = false
                                        } catch (e: Exception) {
                                            Log.e("LIMITLESS_TRACE", "BlindAssistScreen: Obstacle detection failed", e)
                                            ttsManager.speak("Unable to check for obstacles right now.")
                                        } finally {
                                            isProcessingAI = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(72.dp)
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .semantics {
                                    contentDescription = "Detect obstacles button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Obstacles",
                                style = LimitlessTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        // Navigate button
                        Button(
                            onClick = {
                                scope.launch {
                                    val response = BlindNavigationVoice.startNavigation("main entrance")
                                    aiResponse = response
                                    showResponseBanner = true
                                    triggerHaptic()
                                    delay(8000)
                                    showResponseBanner = false
                                }
                            },
                            modifier = Modifier
                                .height(72.dp)
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .semantics {
                                    contentDescription = "Navigate button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Navigate",
                                style = LimitlessTypography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            // Floating Mic FAB button (72dp height, PersonaBlind background)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = {
                        // Cycle through test queries for offline fallback testing
                        val query = testQueries[testQueryIndex]
                        testQueryIndex = (testQueryIndex + 1) % testQueries.size
                        handleAIInvocation(query)
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .semantics {
                            contentDescription = "Ask AI assistant with voice command"
                        },
                    containerColor = PersonaBlind,
                    contentColor = TextPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        } else {
            // Permission denied message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required",
                    color = TextPrimary,
                    style = LimitlessTypography.bodyLarge,
                    modifier = Modifier.semantics {
                        contentDescription = "Camera permission is required to use Blind Assist"
                    }
                )
            }
        }
    }
}
