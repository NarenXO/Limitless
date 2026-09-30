package com.teamdexters.limitless.blind

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Offline color detector.
 * Samples center region and maps to named colors using HSV analysis.
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
            val centerX = bitmap.width / 2
            val centerY = bitmap.height / 2
            val sampleSize = 21
            val halfSample = sampleSize / 2

            var totalRed = 0
            var totalGreen = 0
            var totalBlue = 0
            var pixelCount = 0

            for (x in (centerX - halfSample)..(centerX + halfSample)) {
                for (y in (centerY - halfSample)..(centerY + halfSample)) {
                    if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                        val pixel = bitmap.getPixel(x, y)
                        totalRed += Color.red(pixel)
                        totalGreen += Color.green(pixel)
                        totalBlue += Color.blue(pixel)
                        pixelCount++
                    }
                }
            }

            if (pixelCount > 0) {
                val avgRed = totalRed / pixelCount
                val avgGreen = totalGreen / pixelCount
                val avgBlue = totalBlue / pixelCount
                val rgb = Color.rgb(avgRed, avgGreen, avgBlue)
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
}
