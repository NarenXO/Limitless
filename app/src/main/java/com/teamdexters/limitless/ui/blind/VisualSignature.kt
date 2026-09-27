package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Visual signature extraction and comparison utilities.
 * Creates lightweight signatures from images for landmark recognition.
 * Uses 8x8 grayscale downsampling with average brightness per cell.
 */
object VisualSignature {

    private const val GRID_SIZE = 8 // 8x8 grid for signature
    private const val SIGNATURE_LENGTH = GRID_SIZE * GRID_SIZE // 64 values

    /**
     * Extract a visual signature from a bitmap.
     * Downsamples to 8x8 grayscale and computes average brightness per cell.
     * @param bitmap The image to process
     * @return FloatArray of 64 brightness values (0.0 to 1.0)
     */
    fun extractSignature(bitmap: Bitmap): FloatArray {
        // Downscale to 8x8
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, GRID_SIZE, GRID_SIZE, true)
        
        val signature = FloatArray(SIGNATURE_LENGTH)
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
        return signature
    }

    /**
     * Compare two signatures using Euclidean distance.
     * Lower distance indicates higher similarity.
     * @param signature1 First signature
     * @param signature2 Second signature
     * @return Euclidean distance (0.0 to sqrt(64) ≈ 8.0)
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
        
        return kotlin.math.sqrt(sumSquaredDiff)
    }

    /**
     * Check if two signatures are similar enough based on a threshold.
     * @param signature1 First signature
     * @param signature2 Second signature
     * @param threshold Maximum allowed distance (default 2.0)
     * @return true if signatures are similar, false otherwise
     */
    fun areSignaturesSimilar(
        signature1: FloatArray, 
        signature2: FloatArray, 
        threshold: Float = 2.0f
    ): Boolean {
        return compareSignatures(signature1, signature2) < threshold
    }
}
