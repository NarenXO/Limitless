package com.teamdexters.limitless.blind

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Offline text reader using ML Kit Text Recognition v2.
 * Extracts readable text from camera frames.
 */
object OfflineTextReader {

    private const val TAG = "LIMITLESS_TRACE"

    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    /**
     * Read text from the given bitmap.
     * @param bitmap The image to extract text from
     * @return Extracted text or fallback message
     */
    suspend fun readText(bitmap: Bitmap): String {
        return try {
            // Center Crop 80% ROI
            val cropWidth = (bitmap.width * 0.8).toInt()
            val cropHeight = (bitmap.height * 0.8).toInt()
            val cropX = (bitmap.width - cropWidth) / 2
            val cropY = (bitmap.height - cropHeight) / 2
            val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight)
            
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(croppedBitmap, 0)
            val result = processImage(inputImage)

            if (result.text.isNotBlank()) {
                Log.d(TAG, "OfflineTextReader: Extracted text (cropped): ${result.text.take(50)}...")
                "The text says: ${result.text.trim()}"
            } else {
                // Fallback to full image
                val fullInputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                val fullResult = processImage(fullInputImage)
                
                if (fullResult.text.isNotBlank()) {
                    Log.d(TAG, "OfflineTextReader: Extracted text (full): ${fullResult.text.take(50)}...")
                    "The text says: ${fullResult.text.trim()}"
                } else {
                    Log.d(TAG, "OfflineTextReader: No text detected")
                    "I don't see any readable text in this view."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OfflineTextReader: Error reading text", e)
            "I don't see any readable text in this view."
        }
    }

    /**
     * Process image with ML Kit Text Recognition.
     * @param image The input image
     * @return Text recognition result
     */
    private suspend fun processImage(image: com.google.mlkit.vision.common.InputImage): Text {
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        recognizer.close()
        Log.d(TAG, "OfflineTextReader: Released")
    }
}
