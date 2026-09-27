package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Manages OCR (Optical Character Recognition) using ML Kit Text Recognition.
 * Processes camera frames to extract text from images.
 */
class OCRManager {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Process a bitmap image and extract text from it.
     * @param bitmap The image to process
     * @return The recognized text, or null if no text is found
     */
    suspend fun recognizeText(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            if (result.text.isEmpty()) {
                null
            } else {
                result.text
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Close the recognizer and release resources.
     */
    fun close() {
        recognizer.close()
    }
}

/**
 * Extension function to suspend ML Kit operations.
 */
private suspend fun com.google.mlkit.vision.text.Text.await(): com.google.mlkit.vision.text.Text {
    return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            continuation.resume(result)
        }
        addOnFailureListener { exception ->
            continuation.resumeWithException(exception)
        }
    }
}
