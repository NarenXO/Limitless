package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.util.Log
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.teamdexters.limitless.blind.CameraFrameManager
import java.io.ByteArrayOutputStream

/**
 * Full-screen CameraX preview for blind assistance.
 * Always-on camera with frame capture for real-time object detection.
 *
 * Guarantees:
 * - Camera permission is checked AND re-verified after the launcher callback fires (mutableStateOf).
 * - CameraX (Preview + ImageAnalysis) is bound exactly once per composition lifecycle.
 * - On binding failure: logs LIMITLESS_TRACE error and invokes onError so callers can speak TTS.
 * - ImageAnalysis produces frames at 800ms intervals written to CameraFrameManager.
 * - imageProxy is always closed in finally{}.
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

    // mutableStateOf so a permission grant triggers recomposition and re-evaluates LaunchedEffect.
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Guard: bind CameraX only once even if hasCameraPermission recomposes multiple times.
    var cameraBindingStarted by remember { mutableStateOf(false) }

    // Permission launcher — updates hasCameraPermission so LaunchedEffect(hasCameraPermission) fires.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Log.w("LIMITLESS_TRACE", "BlindCameraPreview: Camera permission denied by user")
            onError(SecurityException("Camera permission denied — vision features unavailable"))
        } else {
            Log.d("LIMITLESS_TRACE", "BlindCameraPreview: Camera permission granted")
        }
    }

    // Request permission on first composition if not already granted.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            Log.d("LIMITLESS_TRACE", "BlindCameraPreview: Requesting CAMERA permission")
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Bind camera as soon as permission is confirmed.
    // Keyed on hasCameraPermission so it re-runs if the user grants after initial denial.
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission && !cameraBindingStarted) {
            cameraBindingStarted = true
            Log.d("LIMITLESS_TRACE", "BlindCameraPreview: Permission confirmed — binding CameraX")
            startCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                onFrameReady = onFrameReady,
                onError = onError
            )
        }
    }

    // CameraX is lifecycle-bound; it auto-releases when the lifecycle owner is destroyed.
    DisposableEffect(Unit) {
        onDispose {
            Log.d("LIMITLESS_TRACE", "BlindCameraPreview: Disposed — CameraX lifecycle will auto-release")
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Bind CameraX Preview + ImageAnalysis to the lifecycle owner.
 * Called exactly once per BlindCameraPreview composition.
 * unbindAll() is called once before the new bind — NOT on every recomposition.
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

            // Preview use case — renders live feed to PreviewView
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // ImageAnalysis use case — 640x480 target, drop frames under load
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(640, 480))
                .build()
                .also {
                    it.setAnalyzer(
                        ContextCompat.getMainExecutor(context),
                        BlindFrameAnalyzer(onFrameReady)
                    )
                }

            // Select rear camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Unbind all existing use cases exactly once before rebinding
            cameraProvider.unbindAll()

            // Bind both Preview and ImageAnalysis simultaneously to the lifecycle
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            Log.d("LIMITLESS_TRACE", "BlindCameraPreview: CameraX bound — Preview + ImageAnalysis active, frames flowing to CameraFrameManager")

        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "CameraX binding failed", e)
            onError(e)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * CameraX ImageAnalysis.Analyzer — converts ImageProxy to Bitmap at ≤1.25 FPS (800ms throttle).
 *
 * Every decoded frame is:
 *  1. Written to CameraFrameManager (singleton) — ensures AI features always have a live frame.
 *  2. Forwarded through the onFrameReady callback — consumed by BlindHomeScreen detection loops.
 *
 * imageProxy is always closed in finally{} to prevent CameraX pipeline stalls.
 */
private class BlindFrameAnalyzer(
    private val onFrameReady: (android.graphics.Bitmap, Int) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "LIMITLESS_TRACE"
        private const val MAX_WIDTH = 640
        private const val MAX_HEIGHT = 480
    }

    private var lastFrameTime = 0L
    private val frameIntervalMs = 800L // ≤1.25 FPS

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()

        // 800ms throttle — drop frames that arrive faster than needed
        if (now - lastFrameTime < frameIntervalMs) {
            image.close()
            return
        }
        lastFrameTime = now

        try {
            val bitmap = imageProxyToBitmap(image, image.imageInfo.rotationDegrees)
            if (bitmap != null) {
                // Push to singleton — all vision features (OCR, describe, navigation) read from here
                CameraFrameManager.updateLatestFrame(bitmap, image.imageInfo.rotationDegrees)
                Log.d(TAG, "CameraFrameManager: Frame captured successfully (${bitmap.width}x${bitmap.height})")

                // Forward to BlindHomeScreen object-detection loop
                onFrameReady(bitmap, image.imageInfo.rotationDegrees)
            } else {
                Log.w(TAG, "BlindFrameAnalyzer: imageProxyToBitmap returned null — skipping frame")
            }
        } catch (e: Exception) {
            Log.e(TAG, "BlindFrameAnalyzer: Error processing frame", e)
        } finally {
            // ALWAYS close to unblock the CameraX pipeline
            image.close()
        }
    }

    /**
     * Convert YUV_420_888 ImageProxy to ARGB_8888 Bitmap, downscaled to max 640x480.
     * Rotation is baked into the bitmap so all consumers receive an upright image.
     */
    private fun imageProxyToBitmap(
        image: ImageProxy,
        rotationDegrees: Int
    ): android.graphics.Bitmap? {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        // NV21 format: Y plane, then V, then U (swapped from YUV_420_888)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
        val imageBytes = out.toByteArray()

        var bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return null

        // Downscale to max 640x480 if the sensor resolution exceeds the target
        if (bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT) {
            val scale = minOf(MAX_WIDTH.toFloat() / bitmap.width, MAX_HEIGHT.toFloat() / bitmap.height)
            val scaledW = (bitmap.width * scale).toInt()
            val scaledH = (bitmap.height * scale).toInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
            bitmap.recycle()
            bitmap = scaled
        }

        // Bake rotation into bitmap so all consumers get an upright image
        return if (rotationDegrees != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            val rotated = android.graphics.Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }
}
