package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetectorOptions
import org.tensorflow.lite.support.common.FileUtil
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
            val isValidModel = modelFile.size > 1000 // Placeholder files are typically small
            
            if (!isValidModel) {
                // Placeholder model detected
                return false
            }
            
            val options = ObjectDetectorOptions.builder()
                .setMaxResults(10)
                .setScoreThreshold(0.5f)
                .build()

            detector = ObjectDetector.createFromOptionsAndFile(options, modelFile)
            isInitialized = true
            true
        } catch (e: IOException) {
            // Model file not found, will use fallback
            false
        } catch (e: Exception) {
            // Other initialization errors (likely invalid model format)
            false
        }
    }

    /**
     * Detect objects in the given bitmap.
     * @param bitmap The image to analyze
     * @return List of detected objects with their properties, or empty list if detection fails
     */
    fun detectObjects(bitmap: Bitmap): List<DetectedObject> {
        if (!isInitialized || detector == null) {
            // Return empty list if not initialized
            return emptyList()
        }

        return try {
            val results = detector?.detect(bitmap) ?: emptyList()
            
            results.map { detection ->
                DetectedObject(
                    label = detection.categories.firstOrNull()?.label ?: "unknown",
                    confidence = detection.categories.firstOrNull()?.score ?: 0f,
                    boundingBox = detection.boundingBox
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Check if the detector is ready to use.
     */
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
    val boundingBox: android.graphics.Rect
)
