package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.IOException

/**
 * Manages object detection using TensorFlow Lite.
 * Uses the EfficientDet Lite0 model for detecting objects in camera frames.
 */
class ObjectDetector(private val context: Context) {

    private var detector: ObjectDetector? = null
    private var isInitialized = false

    /**
     * Initialize the object detector with the EfficientDet Lite0 model.
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(): Boolean {
        return try {
            // Try to load the model from assets
            val modelFile = FileUtil.loadMappedFile(context, "blind/efficientdet_lite0.tflite")
            
            // Check if the file is a valid TFLite model (not a placeholder)
            val isValidModel = modelFile.capacity() > 1000 // Placeholder files are typically small
            
            if (!isValidModel) {
                // Placeholder model detected
                return false
            }
            
            val options = ObjectDetectorOptions.builder()
                .setMaxResults(10)
                .setScoreThreshold(0.35f)
                .build()

            detector = ObjectDetector.createFromBufferAndOptions(modelFile, options)
            isInitialized = true
            true
        } catch (_: IOException) {
            // Model file not found, will use fallback
            false
        } catch (_: Exception) {
            // Other initialization errors (likely invalid model format)
            false
        }
    }

    /**
     * Detect objects in the given bitmap.
     * Filters to keep only objects with confidence >= 0.35 and returns top 4 most confident.
     * @param bitmap The image to analyze
     * @param rotationDegrees The rotation of the image in degrees (0, 90, 180, 270)
     * @return List of detected objects with their properties, or empty list if detection fails
     */
    fun detectObjects(bitmap: Bitmap, rotationDegrees: Int = 0): List<DetectedObject> {
        if (!isInitialized || detector == null) {
            // Return empty list if not initialized
            return emptyList()
        }

        return try {
            // Handle rotation before passing to MediaPipe
            val processedBitmap = if (rotationDegrees != 0 && rotationDegrees != 360) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val tensorImage = TensorImage.fromBitmap(processedBitmap)
            val results: List<Detection> = detector?.detect(tensorImage) ?: emptyList()
            
            // Clean up rotated bitmap if we created one
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            // Filter by confidence >= 0.35 and map to DetectedObject
            val filteredResults = results.mapNotNull { detection ->
                val confidence = detection.categories.firstOrNull()?.score ?: 0f
                if (confidence >= 0.35f) {
                    val boundingBoxRect = Rect()
                    detection.boundingBox.round(boundingBoxRect)

                    DetectedObject(
                        label = normalizeLabel(detection.categories.firstOrNull()?.label ?: "unknown"),
                        confidence = confidence,
                        boundingBox = boundingBoxRect
                    )
                } else {
                    null
                }
            }
            
            // Sort by confidence (descending) and take top 4
            filteredResults.sortedByDescending { it.confidence }.take(4)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Normalize raw model labels to everyday simple words.
     */
    private fun normalizeLabel(label: String): String {
        return when (label.lowercase()) {
            "cell phone", "mobile phone" -> "phone"
            "dining table" -> "table"
            "sofa", "couch" -> "couch"
            "potted plant" -> "plant"
            "laptop" -> "laptop"
            "chair" -> "chair"
            "person" -> "person"
            "bottle" -> "bottle"
            "door" -> "door"
            else -> label.lowercase()
        }
    }

    /**
     * Check if the detector is ready to use.
     */
    @Suppress("unused")
    fun isReady(): Boolean = isInitialized

    /**
     * Close the detector and release resources.
     */
    fun close() {
        detector?.close()
        detector = null
        isInitialized = false
    }
}

/**
 * Data class representing a detected object.
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: Rect
)
