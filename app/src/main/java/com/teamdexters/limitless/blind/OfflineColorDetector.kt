package com.teamdexters.limitless.blind

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.palette.graphics.Palette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline color detector using Android Palette.
 * Extracts dominant color and maps to named colors.
 */
object OfflineColorDetector {

    private const val TAG = "LIMITLESS_TRACE"

    /**
     * Detect the dominant color in the center region of the bitmap.
     * @param bitmap The image to analyze
     * @return Named color description
     */
    suspend fun detectColor(bitmap: Bitmap): String {
        return try {
            // Extract center region for color analysis
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2
            val sampleSize = min(bitmap.width, bitmap.height) / 4
            val sampleX = max(0, centerX - sampleSize / 2)
            val sampleY = max(0, centerY - sampleSize / 2)
            val sampleWidth = min(sampleSize, bitmap.width - sampleX)
            val sampleHeight = min(sampleSize, bitmap.height - sampleY)

            val sampledBitmap = Bitmap.createBitmap(
                bitmap,
                sampleX,
                sampleY,
                sampleWidth,
                sampleHeight
            )

            // Generate palette from sampled region
            val palette = Palette.from(sampledBitmap).generate()

            // Get dominant swatch
            val dominantSwatch = palette.dominantSwatch
                ?: palette.mutedSwatch
                ?: palette.vibrantSwatch

            if (dominantSwatch != null) {
                val rgb = dominantSwatch.rgb
                val colorName = mapRgbToColorName(rgb)
                Log.d(TAG, "OfflineColorDetector: Detected color=$colorName RGB=$rgb")
                "The dominant color in front of you is $colorName."
            } else {
                Log.d(TAG, "OfflineColorDetector: No dominant color found")
                "I cannot determine the dominant color in this view."
            }
        } catch (e: Exception) {
            Log.e(TAG, "OfflineColorDetector: Error detecting color", e)
            "I cannot determine the dominant color in this view."
        }
    }

    /**
     * Map RGB values to named colors using HSV analysis.
     * @param rgb The RGB color value
     * @return Named color string
     */
    private fun mapRgbToColorName(rgb: Int): String {
        val red = Color.red(rgb)
        val green = Color.green(rgb)
        val blue = Color.blue(rgb)

        // Convert RGB to HSV
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        // Check for grayscale colors first
        if (saturation < 0.15f) {
            return when {
                value < 0.15f -> "black"
                value > 0.85f -> "white"
                else -> "gray"
            }
        }

        // Map hue to color name
        val colorName = when {
            hue < 15f || hue >= 345f -> "red"
            hue < 45f -> "orange"
            hue < 75f -> "yellow"
            hue < 150f -> "green"
            hue < 195f -> "cyan"
            hue < 255f -> "blue"
            hue < 285f -> "purple"
            hue < 330f -> "pink"
            else -> "red"
        }

        // Special cases for brown and other colors
        if (colorName == "orange" && saturation < 0.4f && value < 0.6f) {
            return "brown"
        }

        return colorName
    }

    /**
     * Release resources.
     */
    fun release() {
        Log.d(TAG, "OfflineColorDetector: Released")
    }
}
