package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap

/**
 * Handles color detection from camera frames using HSV color space.
 * Samples a 21x21 pixel block at center and maps to named colors using robust thresholds.
 */
class ColorDetector {

    companion object {
        private const val SAMPLE_SIZE = 21
    }

    /**
     * Detect the color at the center of the given bitmap.
     * Samples a 21x21 pixel region around the center and maps to named colors using HSV analysis.
     * @param bitmap The image to sample from
     * @return The name of the detected color
     */
    fun detectColorAtCenter(bitmap: Bitmap): String {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val halfSample = SAMPLE_SIZE / 2

        // Sample pixels in a 21x21 region around the center
        var totalRed = 0
        var totalGreen = 0
        var totalBlue = 0
        var pixelCount = 0

        for (x in (centerX - halfSample)..(centerX + halfSample)) {
            for (y in (centerY - halfSample)..(centerY + halfSample)) {
                if (x >= 0 && x < bitmap.width && y >= 0 && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    totalRed += android.graphics.Color.red(pixel)
                    totalGreen += android.graphics.Color.green(pixel)
                    totalBlue += android.graphics.Color.blue(pixel)
                    pixelCount++
                }
            }
        }

        if (pixelCount == 0) {
            return "unknown"
        }

        // Calculate average RGB values
        val avgRed = totalRed / pixelCount
        val avgGreen = totalGreen / pixelCount
        val avgBlue = totalBlue / pixelCount

        // Convert to HSV for better color classification
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(avgRed, avgGreen, avgBlue, hsv)
        val hue = hsv[0] // 0-360
        val saturation = hsv[1] // 0-1
        val value = hsv[2] // 0-1

        // Apply robust thresholds for non-vibrant colors FIRST
        if (value < 0.20f) {
            return "Black"
        }
        if (value > 0.80f && saturation < 0.15f) {
            return "White"
        }
        if (saturation < 0.15f) {
            return "Gray"
        }

        // Special check for Brown before general hue ranges
        // Brown: Hue 10..40 with low Value (0.20..0.60) and Saturation (0.30..0.85)
        if (hue >= 10f && hue <= 40f && 
            value >= 0.20f && value <= 0.60f && 
            saturation >= 0.30f && saturation <= 0.85f) {
            return "Brown"
        }

        // For vibrant colors (Saturation >= 0.15), use Hue ranges
        return when {
            // Red: 0..15 or 345..360
            (hue >= 0f && hue <= 15f) || (hue >= 345f && hue <= 360f) -> "Red"
            // Orange: 16..45
            hue >= 16f && hue <= 45f -> "Orange"
            // Yellow: 46..70
            hue >= 46f && hue <= 70f -> "Yellow"
            // Green: 71..165
            hue >= 71f && hue <= 165f -> "Green"
            // Blue: 166..255
            hue >= 166f && hue <= 255f -> "Blue"
            // Purple: 256..290
            hue >= 256f && hue <= 290f -> "Purple"
            // Pink: 291..344
            hue >= 291f && hue <= 344f -> "Pink"
            // Fallback for edge cases
            else -> "Red"
        }
    }
}
