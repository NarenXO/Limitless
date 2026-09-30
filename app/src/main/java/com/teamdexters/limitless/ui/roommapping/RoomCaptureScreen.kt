package com.teamdexters.limitless.ui.roommapping

import android.Manifest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
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
                    Text(
                        text = "Room: $roomId",
                        color = Color(0xFF1F1F1F),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
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
                                    text = if (isCaptured) "$corner (Done)" else corner,
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
                    if (currentCornerIndex < 4) {
                        Button(
                            onClick = {
                                val executor = ContextCompat.getMainExecutor(context)
                                imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val path = photoCaptureUtil.savePhoto(roomId, currentCornerIndex, image, image.imageInfo.rotationDegrees)
                                            if (path != null) {
                                                Log.d("LIMITLESS_TRACE", "RoomPhotoCapture: Saved photo $currentCornerIndex for roomId=$roomId")
                                                val newMap = capturedPaths.toMutableMap()
                                                newMap[currentCornerIndex] = path
                                                capturedPaths = newMap
                                                
                                                // Advance
                                                var nextIndex = currentCornerIndex + 1
                                                while (nextIndex < 4 && newMap.containsKey(nextIndex)) {
                                                    nextIndex++
                                                }
                                                currentCornerIndex = nextIndex
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
                            },
                            modifier = Modifier
                                .size(72.dp),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF791A9))
                        ) {
                            Text("Capture", color = Color.White)
                        }
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
