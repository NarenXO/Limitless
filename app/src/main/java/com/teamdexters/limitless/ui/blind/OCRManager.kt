package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import android.graphics.Color
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages OCR (Optical Character Recognition) using ML Kit Text Recognition.
 *
 * Processes camera images and extracts readable text with enhanced preprocessing.
 */
class OCRManager {

    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    /**
     * Process a bitmap image and extract text from it.
     * Applies grayscale conversion and contrast enhancement for better OCR accuracy.
     *
     * @param bitmap The image to process.
     * @param rotationDegrees The rotation of the image in degrees (0, 90, 180, 270).
     * @return Recognized text, or null if no text is found.
     */
    suspend fun recognizeText(bitmap: Bitmap, rotationDegrees: Int = 0): String? {
        return try {
            // Preprocess image for better OCR accuracy
            val processedBitmap = preprocessImage(bitmap)

            // Convert Bitmap to ML Kit InputImage with correct rotation.
            val image = InputImage.fromBitmap(
                processedBitmap,
                rotationDegrees
            )

            // Run ML Kit OCR.
            val result: Text =
                recognizer
                    .process(image)
                    .await()

            // Clean up processed bitmap
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }

            // Return null if no text was detected.
            if (result.text.isBlank()) {
                null
            } else {
                result.text
            }

        } catch (e: Exception) {
            // OCR failed.
            null
        }
    }

    /**
     * Preprocess image for OCR: convert to grayscale and enhance contrast.
     * This improves text recognition accuracy, especially in suboptimal lighting.
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // Create a mutable copy of the bitmap
        val processedBitmap = bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        
        // Convert to grayscale and enhance contrast
        val width = processedBitmap.width
        val height = processedBitmap.height
        val pixels = IntArray(width * height)
        processedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299 + 
                       Color.green(pixel) * 0.587 + 
                       Color.blue(pixel) * 0.114).toInt()
            
            // Apply contrast enhancement
            val contrast = 1.5f
            val enhanced = ((gray - 128) * contrast + 128).toInt().coerceIn(0, 255)
            
            // Set back as grayscale
            pixels[i] = Color.rgb(enhanced, enhanced, enhanced)
        }
        
        processedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return processedBitmap
    }

    /**
     * Close the ML Kit recognizer.
     */
    fun close() {
        recognizer.close()
    }
}


/**
 * Converts a Google Play Services Task into a
 * Kotlin suspend function.
 *
 * ML Kit's process() method returns Task<Text>.
 *
 * This allows us to write:
 *
 * recognizer.process(image).await()
 */
private suspend fun <T> Task<T>.await(): T {

    return suspendCancellableCoroutine { continuation ->

        // Called when the Task succeeds.
        addOnSuccessListener { result ->

            if (continuation.isActive) {
                continuation.resume(result)
            }
        }

        // Called when the Task fails.
        addOnFailureListener { exception ->

            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }

        /*
         * We intentionally do not call cancel() here.
         *
         * Google Play Services Task does not expose the
         * cancel() function required by the previous code.
         */
    }
}