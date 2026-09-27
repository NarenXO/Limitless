package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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
 * CameraX preview composable with frame capture capability.
 * Provides live camera preview and can capture frames for OCR or color detection.
 *
 * @param modifier Modifier for the preview
 * @param showReticle Whether to show a centered reticle overlay (for color detection mode)
 * @param onFrameReady Callback when a frame is ready for processing
 * @param onError Callback for camera errors
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    showReticle: Boolean = false,
    onFrameReady: (Bitmap) -> Unit = {},
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

    LaunchedEffect(lifecycleOwner) {
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

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (showReticle) {
            // Reticle overlay for color detection
            ReticleOverlay()
        }
    }
}

/**
 * Simple reticle overlay for color detection mode.
 * Shows a small centered square to indicate the sampling region.
 */
@Composable
private fun ReticleOverlay() {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasSize = size
            val reticleSize = 50f
            val strokeWidth = 3f

            val centerX = canvasSize.width / 2
            val centerY = canvasSize.height / 2
            val halfReticle = reticleSize / 2

            // Draw a simple square reticle
            drawRect(
                color = androidx.compose.ui.graphics.Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(
                    centerX - halfReticle,
                    centerY - halfReticle
                ),
                size = androidx.compose.ui.geometry.Size(reticleSize, reticleSize),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )
        }
    }
}

/**
 * Start the camera with CameraX.
 */
private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFrameReady: (Bitmap) -> Unit,
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

            // Select back camera as a default
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
 */
private class FrameAnalyzer(
    private val onFrameReady: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastFrameTime = 0L
    private val frameInterval = 500L // Process at most 2 frames per second

    override fun analyze(image: ImageProxy) {
        val currentFrameTime = System.currentTimeMillis()

        // Throttle frame processing to avoid overload
        if (currentFrameTime - lastFrameTime < frameInterval) {
            image.close()
            return
        }

        lastFrameTime = currentFrameTime

        try {
            val bitmap = imageProxyToBitmap(image)
            if (bitmap != null) {
                onFrameReady(bitmap)
            }
        } catch (e: Exception) {
            // Ignore conversion errors
        } finally {
            image.close()
        }
    }

    /**
     * Convert ImageProxy to Bitmap.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
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
        val imageBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}


