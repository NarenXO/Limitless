package com.teamdexters.limitless.blind

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

/**
 * Camera controller for Blind Assist with 1 FPS duty cycling and proximity sensor gating.
 * Processes camera frames at low power and pauses when proximity sensor is covered.
 */
class BlindCameraController(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "LIMITLESS_TRACE"
        private const val FRAME_INTERVAL_MS = 1000L // 1 FPS
        private const val MAX_WIDTH = 640
        private const val MAX_HEIGHT = 480
        private const val JPEG_QUALITY = 70
    }

    private var lastFrameTime = 0L
    private var isCameraPaused = false
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    /**
     * Initialize proximity sensor listener.
     */
    fun initialize() {
        sensorManager = ContextCompat.getSystemService(context, SensorManager::class.java)
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        proximitySensor?.let { sensor ->
            sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "BlindCamera: Proximity sensor registered")
        } ?: run {
            Log.w(TAG, "BlindCamera: Proximity sensor not available")
        }
    }

    /**
     * Create CameraX ImageAnalysis use case with 1 FPS duty cycling.
     */
    fun createImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { imageAnalysis ->
                imageAnalysis.setAnalyzer(
                    androidx.camera.core.ContextCompat.getMainExecutor(context),
                    this::analyzeFrame
                )
            }
    }

    /**
     * Analyze camera frame with 1 FPS duty cycling and proximity gating.
     */
    private fun analyzeFrame(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastFrameTime

        // Check if camera is paused due to proximity sensor
        if (isCameraPaused) {
            imageProxy.close()
            Log.d(TAG, "BlindCamera: Frame skipped (proximity covered)")
            return
        }

        // 1 FPS duty cycling: skip if less than 1000ms elapsed
        if (elapsed < FRAME_INTERVAL_MS) {
            imageProxy.close()
            return
        }

        lastFrameTime = currentTime

        try {
            // Convert ImageProxy to Bitmap with downscaling
            val bitmap = imageProxyToBitmap(imageProxy)

            if (bitmap != null) {
                // Update the singleton with the latest frame
                CameraFrameManager.updateLatestFrame(bitmap, imageProxy.imageInfo.rotationDegrees)

                Log.d(TAG, "BlindCamera: Frame sampled at 1 FPS. Paused=$isCameraPaused, Size=${bitmap.width}x${bitmap.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "BlindCamera: Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Convert ImageProxy to Bitmap with downscaling to max 640x480.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), JPEG_QUALITY, outputStream)
        val jpegBytes = outputStream.toByteArray()

        val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // Downscale to max 640x480 if needed
        return if (bitmap.width > MAX_WIDTH || bitmap.height > MAX_HEIGHT) {
            val scale = minOf(MAX_WIDTH.toFloat() / bitmap.width, MAX_HEIGHT.toFloat() / bitmap.height)
            val scaledWidth = (bitmap.width * scale).toInt()
            val scaledHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else {
            bitmap
        }
    }

    /**
     * SensorEventListener: Handle proximity sensor changes.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_PROXIMITY) {
                val maxRange = it.sensor.maximumRange
                val distance = it.values[0]

                // Proximity covered when distance < maxRange
                val wasPaused = isCameraPaused
                isCameraPaused = distance < maxRange

                if (wasPaused != isCameraPaused) {
                    Log.d(TAG, "BlindCamera: Proximity state changed. Paused=$isCameraPaused, Distance=$distance, MaxRange=$maxRange")
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    /**
     * Check if camera is currently paused.
     */
    fun isPaused(): Boolean = isCameraPaused

    /**
     * Release resources and unregister sensor listener.
     */
    fun release() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        proximitySensor = null
        Log.d(TAG, "BlindCamera: Released proximity sensor")
    }
}
