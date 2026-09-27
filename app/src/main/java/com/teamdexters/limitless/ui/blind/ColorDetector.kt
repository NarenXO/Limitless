package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap

/**
 * Handles color detection from camera frames.
 * Samples pixel regions and maps RGB values to named colors.
 */
class ColorDetector {

    /**
     * Data class representing a named color with its RGB reference values.
     */
    private data class NamedColor(
        val name: String,
        val red: Int,
        val green: Int,
        val blue: Int
    )

    /**
     * Internal color-name table for mapping RGB to named colors.
     */
    private val colorTable = listOf(
        NamedColor("red", 255, 0, 0),
        NamedColor("orange", 255, 165, 0),
        NamedColor("yellow", 255, 255, 0),
        NamedColor("green", 0, 128, 0),
        NamedColor("blue", 0, 0, 255),
        NamedColor("purple", 128, 0, 128),
        NamedColor("pink", 255, 192, 203),
        NamedColor("brown", 165, 42, 42),
        NamedColor("black", 0, 0, 0),
        NamedColor("white", 255, 255, 255),
        NamedColor("gray", 128, 128, 128)
    )

    /**
     * Detect the color at the center of the given bitmap.
     * Samples a small region around the center and maps to the nearest named color.
     * @param bitmap The image to sample from
     * @param sampleSize Size of the region to sample (default 5x5 pixels)
     * @return The name of the detected color
     */
    fun detectColorAtCenter(bitmap: Bitmap, sampleSize: Int = 5): String {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val halfSample = sampleSize / 2

        // Sample pixels in a small region around the center
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

        // Find the nearest named color using Euclidean distance
        return findNearestColor(avgRed, avgGreen, avgBlue)
    }

    /**
     * Find the nearest named color to the given RGB values using Euclidean distance.
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @return The name of the nearest color
     */
    private fun findNearestColor(red: Int, green: Int, blue: Int): String {
        var nearestColor = colorTable[0]
        var minDistance = Double.MAX_VALUE

        for (color in colorTable) {
            val distance = calculateEuclideanDistance(
                red, green, blue,
                color.red, color.green, color.blue
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestColor = color
            }
        }

        return nearestColor.name
    }

    /**
     * Calculate Euclidean distance between two RGB colors.
     */
    private fun calculateEuclideanDistance(
        r1: Int, g1: Int, b1: Int,
        r2: Int, g2: Int, b2: Int
    ): Double {
        val dr = r1 - r2
        val dg = g1 - g2
        val db = b1 - b2
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
