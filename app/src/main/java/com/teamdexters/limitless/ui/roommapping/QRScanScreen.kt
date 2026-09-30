package com.teamdexters.limitless.ui.roommapping

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.teamdexters.limitless.roommapping.QRWaypointScanner
import java.util.Locale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QRScanScreen(
    onRoomDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isManualEntryOpen by remember { mutableStateOf(false) }
    var manualRoomId by remember { mutableStateOf("") }
    
    // Only detect once
    var detected by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val handleDetection = { roomId: String ->
        if (!detected) {
            detected = true
            
            // Vibrate 150ms
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            
            val uniqueId = roomId.removePrefix("LIMITLESS_ROOM_")
            tts?.speak("Room detected: $uniqueId. Ready to capture photos.", TextToSpeech.QUEUE_FLUSH, null, null)
            
            Log.d("LIMITLESS_TRACE", "QRWaypointScanner: Detected roomId=$roomId")
            
            onRoomDetected(roomId)
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F1EE))) {
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

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    ContextCompat.getMainExecutor(ctx),
                                    QRWaypointScanner { rawValue ->
                                        handleDetection(rawValue)
                                    }
                                )
                            }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("LIMITLESS_TRACE", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                }
            )

            // Central QR target box outline in LimitlessPrimary
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(250.dp)
                    .border(width = 4.dp, color = Color(0xFFF791A9), shape = RoundedCornerShape(16.dp))
            )

            // Fallback button below preview
            Button(
                onClick = { isManualEntryOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F))
            ) {
                Text(
                    text = "No QR detected? Tap to enter room name manually.",
                    color = Color(0xFFF7F1EE),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F1EE)), contentAlignment = Alignment.Center) {
            Text("Camera permission is required.", color = Color(0xFF1F1F1F))
        }
    }

    if (isManualEntryOpen) {
        AlertDialog(
            onDismissRequest = { isManualEntryOpen = false },
            title = { Text("Manual Entry", color = Color(0xFF1F1F1F)) },
            text = {
                OutlinedTextField(
                    value = manualRoomId,
                    onValueChange = { manualRoomId = it },
                    label = { Text("Room ID") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    isManualEntryOpen = false
                    val formatId = if (!manualRoomId.startsWith("LIMITLESS_ROOM_")) "LIMITLESS_ROOM_$manualRoomId" else manualRoomId
                    handleDetection(formatId)
                }) {
                    Text("Confirm", color = Color(0xFFF791A9))
                }
            },
            dismissButton = {
                TextButton(onClick = { isManualEntryOpen = false }) {
                    Text("Cancel", color = Color(0xFF1F1F1F))
                }
            },
            containerColor = Color(0xFFF7F1EE)
        )
    }
}
