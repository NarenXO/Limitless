package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Full-screen CameraX preview for blind assistance.
 * Always-on camera with frame capture capability for real-time object detection.
 */
@Composable
fun BlindCameraPreview(
    modifier: Modifier = Modifier,
    onFrameReady: (android.graphics.Bitmap, Int) -> Unit = { _, _ -> },
    onError: (Exception) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }

    // Camera permission check
    val cameraPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // Camera permission launcher
    val cameraPermissionLauncher = remember {
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                onError(SecurityException("Camera permission required"))
            }
        }
    }

    // Request camera permission if not granted
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!cameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Start camera when permission is granted
    androidx.compose.runtime.LaunchedEffect(cameraPermission) {
        if (cameraPermission) {
            startCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                onFrameReady = onFrameReady,
                onError = onError
            )
        } else {
            onError(SecurityException("Camera permission not granted"))
        }
    }

    // Cleanup camera on dispose
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            // Camera will be unbound when composable is disposed
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Start the camera with CameraX for blind assistance.
 */
private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFrameReady: (android.graphics.Bitmap, Int) -> Unit,
    onError: (Exception) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Image analysis use case for frame capture
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        FrameAnalyzer(onFrameReady)
                    )
                }

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

        } catch (e: Exception) {
            onError(e)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * Image analysis analyzer that converts camera frames to Bitmaps.
 * Runs continuously for real-time object detection.
 */
private class FrameAnalyzer(
    private val onFrameReady: (android.graphics.Bitmap, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastFrameTime = 0L
    private val frameInterval = 800L // Process at most 1.25 frames per second (800ms throttling)

    override fun analyze(image: ImageProxy) {
        val currentFrameTime = System.currentTimeMillis()

        // Throttle frame processing to avoid overload (800ms interval)
        if (currentFrameTime - lastFrameTime < frameInterval) {
            image.close()
            return
        }

        lastFrameTime = currentFrameTime

        try {
            val bitmap = imageProxyToBitmap(image, image.imageInfo.rotationDegrees)
            if (bitmap != null) {
                onFrameReady(bitmap, image.imageInfo.rotationDegrees)
            }
        } catch (e: Exception) {
            // Ignore conversion errors
        } finally {
            image.close()
        }
    }

    /**
     * Convert ImageProxy to Bitmap with rotation handling.
     */
    private fun imageProxyToBitmap(image: ImageProxy, rotationDegrees: Int): android.graphics.Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray

        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // Handle rotation
        return if (rotationDegrees != 0 && bitmap != null) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            bitmap.recycle()
            rotatedBitmap
        } else {
            bitmap
        }
    }
}
