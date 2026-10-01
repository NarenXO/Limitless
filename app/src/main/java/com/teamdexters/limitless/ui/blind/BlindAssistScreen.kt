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

    val imageCapture = remember { androidx.camera.core.ImageCapture.Builder().setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

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

    // Auto-Dismiss Result Box
    LaunchedEffect(aiResponse) {
        if (aiResponse.isNotEmpty() && aiResponse != "Processing...") {
            delay(4000L + (aiResponse.length * 60L)) // 4s + reading time
            showResponseBanner = false
        }
    }

    // Handle AI invocation with specific query
    fun handleReadText() {
        ttsManager.stop()
        aiResponse = "Processing..."
        showResponseBanner = true
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
                        
                        // Center Crop 80% ROI
                        val cropWidth = (rotatedBitmap.width * 0.8).toInt()
                        val cropHeight = (rotatedBitmap.height * 0.8).toInt()
                        val cropX = (rotatedBitmap.width - cropWidth) / 2
                        val cropY = (rotatedBitmap.height - cropHeight) / 2
                        val croppedBitmap = Bitmap.createBitmap(rotatedBitmap, cropX, cropY, cropWidth, cropHeight)
                        
                        val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                val text = visionText.text
                                if (text.isNotBlank()) {
                                    aiResponse = "Text reads: $text"
                                    ttsManager.speak("Text reads: $text")
                                    HapticVocabulary.play(context, "SUCCESS")
                                } else {
                                    // Fallback to full image
                                    val fullInputImage = InputImage.fromBitmap(rotatedBitmap, 0)
                                    recognizer.process(fullInputImage)
                                        .addOnSuccessListener { fullVisionText ->
                                            val fullText = fullVisionText.text
                                            if (fullText.isNotBlank()) {
                                                aiResponse = "Text reads: $fullText"
                                                ttsManager.speak("Text reads: $fullText")
                                                HapticVocabulary.play(context, "SUCCESS")
                                            } else {
                                                aiResponse = "No readable text found."
                                                ttsManager.speak("No readable text found. Please hold the document steady in good lighting.")
                                            }
                                        }
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
        ttsManager.stop()
        aiResponse = "Processing..."
        showResponseBanner = true
        if (isProcessingAI) return
        isProcessingAI = true

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

                            val prompt = "You are an AI mobility assistant for a blind person. Analyze this photo for navigation safety.\n" +
                                    "Respond in EXACTLY 3 short sentences using this format:\n" +
                                    "1. PATH STATUS: State clearly if the walking path straight ahead is 'PATH IS CLEAR' or 'PATH IS BLOCKED BY AN OBSTACLE'.\n" +
                                    "2. OBSTACLES: Name specific obstacles directly in front of or near the user (e.g. 'A chair is 1 meter ahead', 'Stairs ahead', 'A table is on your left').\n" +
                                    "3. OBJECTS: List other visible key objects in the room.\n" +
                                    "EXAMPLE:\n" +
                                    "\"Path status: PATH IS CLEAR for 2 meters straight ahead. Obstacles: A wooden chair is on your left. Objects seen: Laptop on desk, doorway in background.\""
                            val geminiClient = GeminiClient()
                            val result = geminiClient.queryGemini(prompt, base64Image)
                            
                            val response = result.getOrNull() ?: "Failed to describe surroundings."
                            
                            aiResponse = response
                            showResponseBanner = true
                            ttsManager.speak(response)
                            HapticVocabulary.play(context, "SUCCESS")
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

    fun handleDetectColor() {
        ttsManager.stop()
        aiResponse = "Processing..."
        showResponseBanner = true
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
                        
                        val color = com.teamdexters.limitless.blind.OfflineColorDetector.detectAccurateColor(rotatedBitmap)
                        
                        aiResponse = "Color detected: $color"
                        ttsManager.speak(aiResponse)
                        HapticVocabulary.play(context, "SUCCESS")
                    } catch (e: Exception) {
                        ttsManager.speak("Failed to detect color.")
                    } finally {
                        isProcessingAI = false
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

    fun handleStartNavigation() {
        ttsManager.stop()
        aiResponse = "Starting navigation..."
        showResponseBanner = true
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
                        
                        // Detect Color button
                        Button(
                            onClick = { handleDetectColor() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .semantics {
                                    contentDescription = "Detect Color button"
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PersonaBlind,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🎨 Detect Color",
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
