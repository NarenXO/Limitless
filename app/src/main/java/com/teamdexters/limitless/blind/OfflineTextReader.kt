package com.teamdexters.limitless.blind

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
 * Extracts readable text from camera frames with grayscale + contrast preprocessing.
 */
object OfflineTextReader {

    private const val TAG = "LIMITLESS_TRACE"

    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    suspend fun readTextRaw(bitmap: Bitmap, rotationDegrees: Int = 0): String? {
        return try {
            val preprocessed = preprocessForOcr(bitmap)
            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(
                preprocessed,
                rotationDegrees
            )
            val result = processImage(inputImage)
            if (preprocessed != bitmap) preprocessed.recycle()

            val extractedText = result.text.trim()
            if (extractedText.isBlank()) null else extractedText
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Read text from the given bitmap.
     * Applies grayscale conversion and 1.5x contrast boost before recognition.
     * Passes correct rotationDegrees into InputImage so ML Kit applies correct orientation.
     *
     * @param bitmap The image to extract text from
     * @param rotationDegrees Clockwise rotation of the frame (0, 90, 180, 270)
     * @return Extracted text formatted as "The text says: ..." or fallback message
     */
    suspend fun readText(bitmap: Bitmap, rotationDegrees: Int = 0): String {
        return try {
            // Preprocess: grayscale + 1.5x contrast boost for better OCR accuracy
            val preprocessed = preprocessForOcr(bitmap)

            val inputImage = com.google.mlkit.vision.common.InputImage.fromBitmap(
                preprocessed,
                rotationDegrees
            )
            val result = processImage(inputImage)

            // Recycle preprocessed bitmap to free memory
            if (preprocessed != bitmap) preprocessed.recycle()

            val extractedText = result.text.trim()
            if (extractedText.isBlank()) {
                Log.d(TAG, "OfflineTextReader: No text detected")
                "No text found in camera view. Move closer to the text."
            } else {
                Log.d(TAG, "OfflineTextReader: Extracted text: ${extractedText.take(50)}...")
                "I see text: $extractedText"
            }
        } catch (e: Exception) {
            Log.e(TAG, "OfflineTextReader: Error reading text", e)
            "No text found in camera view. Move closer to the text."
        }
    }

    /**
     * Preprocess bitmap for OCR:
     * 1. Convert to grayscale via ColorMatrix
     * 2. Boost contrast by 1.5x (scale=1.5, translate=-38 for midpoint shift)
     */
    private fun preprocessForOcr(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Grayscale + 1.5x contrast matrix
        val colorMatrix = ColorMatrix()

        // Step 1: grayscale
        colorMatrix.setSaturation(0f)

        // Step 2: contrast scale 1.5 with midpoint correction
        // contrast = 1.5: scale by 1.5, shift by (1 - 1.5) * 128 = -64
        val contrast = 1.5f
        val shift = (1f - contrast) * 128f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f
            )
        )
        contrastMatrix.preConcat(colorMatrix)

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(contrastMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Process image with ML Kit Text Recognition.
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
