package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Visual signature extraction and comparison utilities.
 * Creates lightweight multi-feature signatures from images for landmark recognition.
 * Uses combined 8x8 grayscale grid + 8-bin RGB color histogram.
 */
object VisualSignature {

    private const val GRID_SIZE = 8 // 8x8 grid for signature
    private const val GRAYSCALE_LENGTH = GRID_SIZE * GRID_SIZE // 64 values
    private const val HISTOGRAM_BINS = 8 // 8 color channel bins
    private const val SIGNATURE_LENGTH = GRAYSCALE_LENGTH + HISTOGRAM_BINS // 72 values total

    /**
     * Extract a multi-feature visual signature from a bitmap.
     * Combines 8x8 grayscale cell brightness array + 8-bin RGB color histogram.
     * @param bitmap The image to process
     * @return FloatArray of 72 values (64 grayscale + 8 histogram)
     */
    fun extractSignature(bitmap: Bitmap): FloatArray {
        val signature = FloatArray(SIGNATURE_LENGTH)
        
        // Part 1: 8x8 grayscale cell brightness array (64 values)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, GRID_SIZE, GRID_SIZE, true)
        var index = 0
        
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val pixel = scaledBitmap.getPixel(x, y)
                
                // Convert to grayscale using luminance formula
                val gray = (Color.red(pixel) * 0.299 + 
                           Color.green(pixel) * 0.587 + 
                           Color.blue(pixel) * 0.114).toInt()
                
                // Normalize to 0.0-1.0 range
                signature[index] = gray / 255.0f
                index++
            }
        }
        
        scaledBitmap.recycle()
        
        // Part 2: 8-bin RGB color histogram (8 values)
        val histogram = extractColorHistogram(bitmap)
        for (i in histogram.indices) {
            signature[index] = histogram[i]
            index++
        }
        
        return signature
    }

    /**
     * Extract a lightweight 8-bin RGB color histogram from the bitmap.
     * Divides RGB space into 8 bins and counts pixel distribution.
     * @param bitmap The image to process
     * @return FloatArray of 8 normalized histogram values
     */
    private fun extractColorHistogram(bitmap: Bitmap): FloatArray {
        val histogram = IntArray(8)
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        
        // Sample pixels (every 10th pixel for performance)
        val sampleStep = 10
        var sampledPixels = 0
        
        for (y in 0 until height step sampleStep) {
            for (x in 0 until width step sampleStep) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Simple binning: combine RGB into 8 bins based on dominant channel and intensity
                val binIndex = when {
                    r > g && r > b -> if (r > 170) 0 else 1 // Red bright/dim
                    g > r && g > b -> if (g > 170) 2 else 3 // Green bright/dim
                    b > r && b > g -> if (b > 170) 4 else 5 // Blue bright/dim
                    else -> if ((r + g + b) / 3 > 170) 6 else 7 // Neutral bright/dim
                }
                
                histogram[binIndex]++
                sampledPixels++
            }
        }
        
        // Normalize histogram to 0.0-1.0 range
        val normalizedHistogram = FloatArray(8)
        for (i in histogram.indices) {
            normalizedHistogram[i] = if (sampledPixels > 0) {
                histogram[i].toFloat() / sampledPixels
            } else {
                0f
            }
        }
        
        return normalizedHistogram
    }

    /**
     * Compare two signatures using normalized Euclidean distance.
     * Lower distance indicates higher similarity.
     * @param signature1 First signature
     * @param signature2 Second signature
     * @return Normalized Euclidean distance (0.0 to 1.0)
     */
    fun compareSignatures(signature1: FloatArray, signature2: FloatArray): Float {
        if (signature1.size != signature2.size) {
            return Float.MAX_VALUE
        }
        
        var sumSquaredDiff = 0f
        for (i in signature1.indices) {
            val diff = signature1[i] - signature2[i]
            sumSquaredDiff += diff * diff
        }
        
        // Normalize by signature length for consistent 0.0-1.0 range
        return kotlin.math.sqrt(sumSquaredDiff / signature1.size)
    }

    /**
     * Convert Euclidean distance to similarity score (0% to 100%).
     * Higher score indicates higher similarity.
     * @param distance Normalized Euclidean distance (0.0 to 1.0)
     * @return Similarity score as percentage (0.0 to 100.0)
     */
    fun distanceToSimilarity(distance: Float): Float {
        // Clamp distance to valid range
        val clampedDistance = distance.coerceIn(0f, 1f)
        // Convert to similarity: distance 0.0 -> 100%, distance 1.0 -> 0%
        return (1.0f - clampedDistance) * 100f
    }

    /**
     * Average two signatures together.
     * Used for dual-frame averaging during landmark tagging.
     * @param signature1 First signature
     * @param signature2 Second signature
     * @return Averaged signature
     */
    fun averageSignatures(signature1: FloatArray, signature2: FloatArray): FloatArray {
        if (signature1.size != signature2.size) {
            return signature1
        }
        
        val averaged = FloatArray(signature1.size)
        for (i in signature1.indices) {
            averaged[i] = (signature1[i] + signature2[i]) / 2f
        }
        
        return averaged
    }
}
