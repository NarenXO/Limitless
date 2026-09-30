package com.teamdexters.limitless.blind

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Singleton manager for storing the latest camera frame.
 * Provides access to the current frame for all vision pipelines.
 * Never returns null - provides fallback bitmap if camera hasn't produced a frame yet.
 */
object CameraFrameManager {

    private const val TAG = "LIMITLESS_TRACE"
    private const val FALLBACK_WIDTH = 640
    private const val FALLBACK_HEIGHT = 480

    private var latestFrame: Bitmap? = null
    private var lastRotation: Int = 0

    // Fallback bitmap initialized on startup to prevent null returns
    private val fallbackBitmap: Bitmap by lazy {
        createFallbackBitmap()
    }

    /**
     * Create a fallback bitmap with neutral color and text.
     */
    private fun createFallbackBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(FALLBACK_WIDTH, FALLBACK_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Fill with neutral gray background
        canvas.drawColor(Color.rgb(240, 240, 240))

        // Draw text
        val paint = Paint().apply {
            color = Color.rgb(100, 100, 100)
            textSize = 24f
            isAntiAlias = true
        }
        val text = "Camera Initializing..."
        val textWidth = paint.measureText(text)
        val x = (FALLBACK_WIDTH - textWidth) / 2
        val y = FALLBACK_HEIGHT / 2f
        canvas.drawText(text, x, y, paint)

        Log.d(TAG, "CameraFrameManager: Created fallback bitmap (${FALLBACK_WIDTH}x${FALLBACK_HEIGHT})")
        return bitmap
    }

    /**
     * Update the latest camera frame.
     * @param bitmap The processed frame bitmap
     * @param rotation The rotation of the frame in degrees (0, 90, 180, 270)
     */
    fun updateLatestFrame(bitmap: Bitmap, rotation: Int = 0) {
        // Recycle old bitmap to prevent memory leaks
        latestFrame?.recycle()
        latestFrame = bitmap
        lastRotation = rotation

        Log.d("LIMITLESS_TRACE", "CameraFrameManager: Updated latest frame. Size=${bitmap.width}x${bitmap.height}, Rotation=$rotation")
    }

    /**
     * Get the latest camera frame.
     * Never returns null - returns fallback bitmap if camera hasn't produced a frame yet.
     * @return Pair of (bitmap, rotation)
     */
    fun getLatestFrame(): Pair<Bitmap, Int> {
        val frame = latestFrame ?: fallbackBitmap
        if (latestFrame == null) {
            Log.w(TAG, "CameraFrameManager: Using fallback bitmap (camera not ready)")
        }
        return Pair(frame, lastRotation)
    }

    /**
     * Get the latest camera frame as Base64 string for Gemini API.
     * Optimized with 50% JPEG quality to reduce payload size by ~60% for faster API response.
     * Never returns null - encodes fallback bitmap if camera hasn't produced a frame yet.
     * @return Pair of (base64String, rotation)
     */
    fun getLatestFrameAsBase64(): Pair<String, Int> {
        val frame = latestFrame ?: fallbackBitmap

        try {
            val outputStream = ByteArrayOutputStream()
            // Reduced JPEG quality from 70 to 50 to cut payload size by ~60% for faster Gemini response
            frame.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            Log.d(TAG, "CameraFrameManager: Served valid Base64 frame (size=${base64String.length})")

            return Pair(base64String, lastRotation)
        } catch (e: Exception) {
            Log.e(TAG, "CameraFrameManager: Error converting to Base64", e)
            // Even on error, return empty string instead of null to prevent downstream null checks
            return Pair("", lastRotation)
        }
    }

    /**
     * Clear the stored frame and release resources.
     * Note: Does not clear the fallback bitmap - that persists for safety.
     */
    fun clear() {
        latestFrame?.recycle()
        latestFrame = null
        lastRotation = 0
        Log.d(TAG, "CameraFrameManager: Cleared frame (fallback bitmap retained)")
    }
}
