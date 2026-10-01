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
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import android.content.Context
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.teamdexters.limitless.blind.BlindAIInvoker
import com.teamdexters.limitless.blind.BlindCameraController
import com.teamdexters.limitless.ui.components.HazelResponseBanner
import com.teamdexters.limitless.ui.theme.*
import com.teamdexters.limitless.blind.HapticVocabulary
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Blind Assist Screen with CameraX PreviewView.
 * Features 1 FPS camera duty cycling, proximity sensor gating, and on-demand AI invocation.
 * Full-screen camera with translucent top bar, result banner, bottom action bar, and floating mic button.
 */
@Composable
fun BlindAssistScreen(navController: androidx.navigation.NavHostController) {
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
    var cameraStatus by remember { mutableStateOf("Camera Active") }

    val imageCapture = remember { androidx.camera.core.ImageCapture.Builder().build() }

    // AI invocation state
    var aiResponse by remember { mutableStateOf("") }
    var showResponseBanner by remember { mutableStateOf(false) }
    var isProcessingAI by remember { mutableStateOf(false) }
    var testQueryIndex by remember { mutableIntStateOf(0) }

    // Test queries for Phase 3 offline fallback testing
    val testQueries = listOf(
        "describe surroundings",
        "what's in front",
        "read text",
        "detect obstacles",
        "what color"
    )

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
            cameraStatus = if (isCameraPaused) "Camera Paused" else "Camera Active"
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

    // Handle AI invocation with specific query
    fun handleReadText() {
        if (isProcessingAI) return
        isProcessingAI = true
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.capacity())
                        buffer.get(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                        
                        val matrix = Matrix()
                        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        
                        val inputImage = InputImage.fromBitmap(rotatedBitmap, 0)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val text = visionText.text
                                if (text.isNotBlank()) {
                                    aiResponse = text
                                    showResponseBanner = true
                                    ttsManager.speak(text)
                                    HapticVocabulary.play(context, "SUCCESS")
                                    scope.launch {
                                        delay(maxOf(3000L, text.length * 60L))
                                        showResponseBanner = false
                                    }
                                } else {
                                    ttsManager.speak("No text detected.")
                                }
                                isProcessingAI = false
                            }
                            .addOnFailureListener { e ->
                                ttsManager.speak("Failed to read text.")
                                isProcessingAI = false
                            }
                    } catch (e: Exception) {
                        isProcessingAI = false
                    } finally {
                        image.close()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    isProcessingAI = false
                    ttsManager.speak("Failed to capture image.")
                }
            }
        )
    }

    fun handleDescribeSurroundings() {
        if (isProcessingAI) return
        isProcessingAI = true
        aiResponse = "Analyzing scene..."
        showResponseBanner = true

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    scope.launch {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.capacity())
                            buffer.get(bytes)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)
                            
                            val matrix = Matrix()
                            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
                            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

                            val outputStream = ByteArrayOutputStream()
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                            val prompt = "You are an AI navigation guide for a blind person. Analyze this camera frame for physical navigation and spatial awareness.\n" +
                                    "Respond in 1-2 SHORT, DIRECT sentences following this exact structure:\n" +
                                    "1) Free space status: State if the walking path straight ahead is CLEAR or BLOCKED, and for how many meters.\n" +
                                    "2) Specific obstacles: Name specific objects and their position (left, right, or straight ahead).\n" +
                                    "Example 1: \"Path is clear for 2 meters straight ahead. A wooden chair is on your left, and a table is 1 meter ahead.\"\n" +
                                    "Example 2: \"Path is blocked. A closed door is directly in front of you, and a backpack is on the floor to your right.\"\n" +
                                    "Do NOT give generic answers like 'dark object' or 'indoor scene'. Be precise about free space and obstacles."
                            val geminiClient = GeminiClient()
                            val result = geminiClient.queryGemini(prompt, base64Image)
                            
                            val response = result.getOrNull() ?: "Failed to describe surroundings."
                            
                            aiResponse = response
                            showResponseBanner = true
                            ttsManager.speak(response)
                            HapticVocabulary.play(context, "SUCCESS")
                            
                            delay(maxOf(3000L, response.length * 60L))
                            showResponseBanner = false
                        } catch (e: Exception) {
                            ttsManager.speak("Failed to describe surroundings.")
                            showResponseBanner = false
                        } finally {
                            isProcessingAI = false
                            image.close()
                        }
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    isProcessingAI = false
                    showResponseBanner = false
                    ttsManager.speak("Failed to capture image.")
                }
            }
        )
    }

    fun handleStartNavigation() {
        ttsManager.speak("Navigating to mobility menu. Please select your destination to begin turn-by-turn guidance.")
        navController.navigate("mobility-home")
    }

    // Handle AI invocation with specific query


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
                                imageAnalysis,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }, ContextCompat.getMainExecutor(context))
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isProcessingAI) {
                            CircularProgressIndicator(color = PersonaBlind)
                        }

                        // Read Text button
                        Button(
                            onClick = { handleReadText() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .semantics {
                                    contentDescription = "Read Text button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "📖 Read Text",
                                style = LimitlessTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }
                        
                        // Describe Surroundings button
                        Button(
                            onClick = { handleDescribeSurroundings() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .semantics {
                                    contentDescription = "Describe Surroundings button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "👁️ Describe Surroundings",
                                style = LimitlessTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }

                        // Start Navigation button
                        Button(
                            onClick = { handleStartNavigation() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .semantics {
                                    contentDescription = "Start Voice Navigation button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🧭 Start Voice Navigation",
                                style = LimitlessTypography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        }
                    }
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
