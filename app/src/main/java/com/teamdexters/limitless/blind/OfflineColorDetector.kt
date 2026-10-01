package com.teamdexters.limitless.blind

import android.graphics.Bitmap

object OfflineColorDetector {
    fun detectAccurateColor(bitmap: Bitmap): String {
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val sampleRadius = 25 // 50x50 grid

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var pixelCount = 0

        val startX = (centerX - sampleRadius).coerceAtLeast(0)
        val endX = (centerX + sampleRadius).coerceAtMost(bitmap.width - 1)
        val startY = (centerY - sampleRadius).coerceAtLeast(0)
        val endY = (centerY + sampleRadius).coerceAtMost(bitmap.height - 1)

        for (x in startX..endX) {
            for (y in startY..endY) {
                val pixel = bitmap.getPixel(x, y)
                totalR += (pixel shr 16 and 0xFF)
                totalG += (pixel shr 8 and 0xFF)
                totalB += (pixel and 0xFF)
                pixelCount++
            }
        }

        if (pixelCount == 0) return "Unknown Color"

        val avgR = (totalR / pixelCount).toInt()
        val avgG = (totalG / pixelCount).toInt()
        val avgB = (totalB / pixelCount).toInt()

        return mapRgbToHumanColor(avgR, avgG, avgB)
    }

    private fun mapRgbToHumanColor(r: Int, g: Int, b: Int): String {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        
        if (max - min < 20) {
            if (max > 200) return "White"
            if (max < 50) return "Black"
            return "Grey"
        }
        
        if (max == r) {
            if (g > 150 && b < 100) return "Yellow"
            if (g > 50 && b < 50) return "Orange"
            if (b > 150 && g < 100) return "Magenta"
            return "Red"
        }
        
        if (max == g) {
            if (b > 150) return "Cyan"
            if (r > 150) return "Yellow-Green"
            return "Green"
        }
        
        if (r > 150) return "Purple"
        return "Blue"
    }

    fun detectColor(bitmap: Bitmap): String {
        return detectAccurateColor(bitmap)
    }

    fun release() {
        // No-op
    }
}
