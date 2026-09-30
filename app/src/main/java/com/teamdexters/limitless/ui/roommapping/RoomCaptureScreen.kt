package com.teamdexters.limitless.ui.roommapping

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.teamdexters.limitless.roommapping.RoomPhotoCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RoomCaptureScreen(
    roomId: String,
    onAnalyzeRoom: (List<String>, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val imageCapture = remember { ImageCapture.Builder().build() }
    val photoCaptureUtil = remember { RoomPhotoCapture(context) }
    
    val corners = listOf("North", "East", "South", "West")
    var capturedPaths by remember { mutableStateOf(mutableMapOf<Int, String>()) }
    var currentCornerIndex by remember { mutableStateOf(0) }

    var isBlindFriendlyMode by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    
    var currentAzimuth by remember { mutableStateOf(0f) }
    var targetAzimuth by remember { mutableStateOf(-1f) }

    DisposableEffect(Unit) {
        val textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        tts = textToSpeech
        onDispose {
            textToSpeech.shutdown()
        }
    }

    DisposableEffect(isBlindFriendlyMode) {
        var sensorManager: SensorManager? = null
        var listener: SensorEventListener? = null

        if (isBlindFriendlyMode) {
            sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager
            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val orientationValues = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientationValues)
                        
                        // Convert azimuth to degrees
                        var azimuth = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                        if (azimuth < 0) azimuth += 360f
                        currentAzimuth = azimuth

                        if (targetAzimuth != -1f) {
                            var diff = abs(currentAzimuth - targetAzimuth)
                            if (diff > 180f) diff = 360f - diff
                            
                            if (diff < 15f) {
                                // Close to target
                                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                    vibratorManager.defaultVibrator
                                } else {
                                    @Suppress("DEPRECATION")
                                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                                }
                                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                            }
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            
            rotationSensor?.let {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
            
            // Initial instructions
            tts?.speak("Blind-Friendly Mode enabled. Face North to begin. Tap anywhere to capture North corner.", TextToSpeech.QUEUE_FLUSH, null, null)
            targetAzimuth = 0f
        }
        
        onDispose {
            listener?.let { sensorManager?.unregisterListener(it) }
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    fun handleCapture() {
        if (currentCornerIndex >= 4) return

        val executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                coroutineScope.launch(Dispatchers.IO) {
                    val path = photoCaptureUtil.savePhoto(roomId, currentCornerIndex, image, image.imageInfo.rotationDegrees)
                    if (path != null) {
                        Log.d("LIMITLESS_TRACE", "RoomPhotoCapture: Saved photo currentCornerIndex for roomId=roomId")
                        val newMap = capturedPaths.toMutableMap()
                        newMap[currentCornerIndex] = path
                        capturedPaths = newMap
                        
                        val prevCorner = corners[currentCornerIndex]
                        
                        // Advance
                        var nextIndex = currentCornerIndex + 1
                        while (nextIndex < 4 && newMap.containsKey(nextIndex)) {
                            nextIndex++
                        }
                        currentCornerIndex = nextIndex

                        if (isBlindFriendlyMode) {
                            if (currentCornerIndex < 4) {
                                val nextCorner = corners[currentCornerIndex]
                                targetAzimuth = (targetAzimuth + 90f) % 360f
                                tts?.speak("prevCorner captured. Turn 90 degrees right for nextCorner. Tap anywhere to capture.", TextToSpeech.QUEUE_FLUSH, null, null)
                            } else {
                                tts?.speak("All corners captured. Tap Analyze Room.", TextToSpeech.QUEUE_FLUSH, null, null)
                            }
                        }
                    }
                }
                
                // Vibrate
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vibratorManager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("LIMITLESS_TRACE", "Capture failed", exception)
            }
        })
    }

    if (cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F1EE))
                .clickable(enabled = isBlindFriendlyMode) { handleCapture() }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (e: Exception) {
                            Log.e("LIMITLESS_TRACE", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                }
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCCF7F1EE))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Room: roomId",
                            color = Color(0xFF1F1F1F),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Blind-Friendly Guidance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF1F1F1F)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isBlindFriendlyMode,
                                onCheckedChange = { isBlindFriendlyMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFF791A9),
                                    checkedTrackColor = Color(0xFFFFE797)
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        corners.forEachIndexed { index, corner ->
                            val isCaptured = capturedPaths.containsKey(index)
                            val isActive = index == currentCornerIndex
                            
                            val bgColor = if (isCaptured) Color(0xFFFFE797) else Color(0xFFE0F2F4)
                            val borderColor = if (isActive) Color(0xFFF791A9) else Color.Transparent

                            Box(
                                modifier = Modifier
                                    .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                                    .background(bgColor, RoundedCornerShape(16.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (isCaptured) "corner (Done)" else corner,
                                    color = Color(0xFF1F1F1F),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isBlindFriendlyMode && currentCornerIndex < 4) {
                        Button(
                            onClick = { handleCapture() },
                            modifier = Modifier
                                .size(72.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9))
                        ) {
                            Text("Capture", color = Color.White)
                        }
                    }

                    if (isBlindFriendlyMode && currentCornerIndex < 4) {
                        Text(
                            text = "Tap ANYWHERE on screen to capture",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val allCaptured = capturedPaths.size == 4
                    Button(
                        onClick = {
                            if (allCaptured) {
                                val paths = (0..3).map { capturedPaths[it]!! }
                                onAnalyzeRoom(paths, roomId)
                            }
                        },
                        enabled = allCaptured,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allCaptured) Color(0xFFF791A9) else Color.Gray,
                            contentColor = Color(0xFFF7F1EE)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Analyze Room with AI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F1EE)), contentAlignment = Alignment.Center) {
            Text("Camera permission is required.", color = Color(0xFF1F1F1F))
        }
    }
}
