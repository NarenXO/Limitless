package com.teamdexters.limitless.blind

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log

/**
 * Offline color detector.
 * Samples a 21x21 center pixel region, averages RGB, converts to HSV,
 * and maps to named colors using the priority-ordered HSV ruleset.
 *
 * Priority order:
 *  1. Non-vibrant: black / white / gray (checked first regardless of hue)
 *  2. Vibrant brown (hue 10-40, mid Value, mid-high Saturation)
 *  3. Hue-angle color mapping
 */
object OfflineColorDetector {

    private const val TAG = "LIMITLESS_TRACE"

    /**
     * Detect the dominant color in the center region of the bitmap.
     * Samples a 21x21 pixel center region to reduce single-pixel noise.
     * @param bitmap The image to analyze
     * @return Human-readable color description
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
                val colorName = mapRgbToColorName(avgRed, avgGreen, avgBlue)
                Log.d(TAG, "OfflineColorDetector: Detected color=$colorName RGB=($avgRed,$avgGreen,$avgBlue)")
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
     * Map average RGB values to a named color using HSV analysis.
     *
     * Priority:
     *  1. Non-vibrant check (Value < 0.20 → Black, Value > 0.80 && Sat < 0.15 → White, Sat < 0.15 → Gray)
     *  2. Special Brown check (Hue 10-40, Value 0.20-0.60, Sat 0.30-0.85)
     *  3. Hue-angle mapping for vibrant colors
     */
    private fun mapRgbToColorName(red: Int, green: Int, blue: Int): String {
        val hsv = FloatArray(3)
        Color.RGBToHSV(red, green, blue, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        // --- Priority 1: Non-vibrant colors (check FIRST regardless of hue) ---
        if (value < 0.20f) return "Black"
        if (value > 0.80f && saturation < 0.15f) return "White"
        if (saturation < 0.15f) return "Gray"

        // --- Priority 2: Brown check (vibrant but dark-ish orange-ish hue) ---
        if (hue in 10f..40f && value in 0.20f..0.60f && saturation in 0.30f..0.85f) {
            return "Brown"
        }

        // --- Priority 3: Vibrant hue-angle mapping ---
        return when {
            hue <= 15f || hue >= 345f -> "Red"
            hue <= 45f               -> "Orange"
            hue <= 70f               -> "Yellow"
            hue <= 165f              -> "Green"
            hue <= 255f              -> "Blue"
            hue <= 290f              -> "Purple"
            hue <= 344f              -> "Pink"
            else                     -> "Red"
        }
    }
}
