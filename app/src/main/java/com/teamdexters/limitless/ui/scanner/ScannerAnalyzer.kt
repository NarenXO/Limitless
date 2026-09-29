package com.teamdexters.limitless.ui.scanner

import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

data class ScanObjectResult(val label: String, val confidence: Float)

class ScannerAnalyzer {
    
    suspend fun analyzeFrame(bitmap: Bitmap, rotationDegrees: Int): Triple<List<ScanObjectResult>, List<String>, Int> = withContext(Dispatchers.Default) {
        val objectsDeferred = async { detectObjects(bitmap, rotationDegrees) }
        val textDeferred = async { recognizeSignageText(bitmap, rotationDegrees) }
        val lightingDeferred = async { calculateLightingScore(bitmap) }
        
        // Wait for all analyses to complete in parallel
        val objects = objectsDeferred.await()
        val text = textDeferred.await()
        val lighting = lightingDeferred.await()
        
        Triple(objects, text, lighting)
    }

    suspend fun detectObjects(bitmap: Bitmap, rotationDegrees: Int): List<ScanObjectResult> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, rotationDegrees)
        val labeler = com.google.mlkit.vision.label.ImageLabeling.getClient(
            com.google.mlkit.vision.label.defaults.ImageLabelerOptions.DEFAULT_OPTIONS
        )
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val results = labels.map { ScanObjectResult(it.text, it.confidence) }
                continuation.resume(results, null)
            }
            .addOnFailureListener {
                continuation.resume(emptyList(), null)
            }
    }

    suspend fun recognizeSignageText(bitmap: Bitmap, rotationDegrees: Int): List<String> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, rotationDegrees)
        val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
        )
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val allText = visionText.textBlocks.map { it.text }
                continuation.resume(allText, null)
            }
            .addOnFailureListener {
                continuation.resume(emptyList(), null)
            }
    }

    suspend fun calculateLightingScore(bitmap: Bitmap): Int = withContext(Dispatchers.Default) {
        // Calculates average frame brightness (returns 0-100 score)
        // Subsampling for performance (check 100 pixels)
        var totalLuminance = 0L
        val stepX = Math.max(1, bitmap.width / 10)
        val stepY = Math.max(1, bitmap.height / 10)
        var count = 0
        
        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Standard relative luminance
                val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
                totalLuminance += luminance
                count++
            }
        }
        
        val avgLuminance = if (count > 0) (totalLuminance / count).toInt() else 0
        // Map 0-255 luminance to 0-100 score
        val score = (avgLuminance * 100) / 255
        score.coerceIn(0, 100)
    }
}
