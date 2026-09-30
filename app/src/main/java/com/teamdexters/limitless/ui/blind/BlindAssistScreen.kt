package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Preview
import androidx.camera.core.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.teamdexters.limitless.blind.BlindAIInvoker
import com.teamdexters.limitless.blind.BlindCameraController
import com.teamdexters.limitless.ui.components.HazelResponseBanner
import com.teamdexters.limitless.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blind Assist Screen with CameraX PreviewView.
 * Features 1 FPS camera duty cycling, proximity sensor gating, and on-demand AI invocation.
 */
@Composable
fun BlindAssistScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera permission handling
    val cameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Camera controller
    val cameraController = remember { BlindCameraController(context) }
    var isCameraPaused by remember { mutableStateOf(false) }

    // AI invocation state
    var aiResponse by remember { mutableStateOf("") }
    var showResponseBanner by remember { mutableStateOf(false) }
    var isProcessingAI by remember { mutableStateOf(false) }

    // TTS manager
    val ttsManager = remember { TTSManager(context) }

    // Initialize camera controller and AI invoker
    LaunchedEffect(Unit) {
        cameraController.initialize()
        BlindAIInvoker.initialize(context)
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
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraController.release()
            BlindAIInvoker.release()
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

    // Handle AI invocation
    fun handleAIInvocation() {
        if (isProcessingAI) return

        scope.launch {
            isProcessingAI = true
            try {
                // Default query for Phase 2 (Phase 3: will use voice input)
                val query = "describe surroundings"
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
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
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
                        } catch (e: Exception) {
                            Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )

            // Proximity pause indicator
            if (isCameraPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = SurfaceTint,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Camera Paused (Proximity Covered)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // AI response banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                HazelResponseBanner(
                    text = aiResponse,
                    isVisible = showResponseBanner && aiResponse.isNotEmpty(),
                    onDismiss = { showResponseBanner = false },
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Mic FAB button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = { handleAIInvocation() },
                    modifier = Modifier.size(72.dp),
                    containerColor = PersonaBlind,
                    contentColor = TextPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Ask AI",
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
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
