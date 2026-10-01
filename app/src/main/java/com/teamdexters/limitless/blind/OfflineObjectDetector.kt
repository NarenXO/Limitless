package com.teamdexters.limitless.blind

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.IOException

/**
 * Offline object detector using TensorFlow Lite EfficientDet Lite0.
 * Detects objects and maps them to spatial buckets (left/ahead/right).
 */
object OfflineObjectDetector {

    private const val TAG = "LIMITLESS_TRACE"
    private const val MODEL_PATH = "blind/efficientdet_lite0.tflite"
    private const val MAX_RESULTS = 5
    private const val CONFIDENCE_THRESHOLD = 0.50f

    private var detector: ObjectDetector? = null
    private var isInitialized = false

    /**
     * Initialize the object detector with the TFLite model.
     * @param context Android context
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(context: Context): Boolean {
        return try {
            val modelFile = FileUtil.loadMappedFile(context, MODEL_PATH)

            // Check if the file is a valid TFLite model (not a placeholder)
            val isValidModel = modelFile.capacity() > 1000

            if (!isValidModel) {
                Log.w(TAG, "OfflineObjectDetector: Placeholder model detected, using fallback")
                return false
            }

            val options = ObjectDetectorOptions.builder()
                .setMaxResults(MAX_RESULTS)
                .setScoreThreshold(CONFIDENCE_THRESHOLD)
                .build()

            detector = ObjectDetector.createFromBufferAndOptions(modelFile, options)
            isInitialized = true
            Log.d(TAG, "OfflineObjectDetector: Initialized successfully")
            true
        } catch (e: IOException) {
            Log.e(TAG, "OfflineObjectDetector: Model file not found", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "OfflineObjectDetector: Initialization error", e)
            false
        }
    }

    /**
     * Detect objects in the given bitmap and generate a spatial sentence.
     * @param bitmap The image to analyze
     * @return Spatial description of detected objects
     */
    fun detectObjects(bitmap: Bitmap): String {
        if (!isInitialized || detector == null) {
            Log.w(TAG, "OfflineObjectDetector: Detector not initialized")
            return "I don't see any distinct objects in front of you."
        }

        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val detections = detector?.detect(tensorImage) ?: emptyList()

            Log.d(TAG, "OfflineObjectDetector: Detected ${detections.size} objects")

            if (detections.isEmpty()) {
                return "I don't see any distinct objects in front of you."
            }

            // Map detections to spatial buckets
            val spatialBuckets = detections.groupBy { detection ->
                val boundingBox = detection.boundingBox
                val centerX = boundingBox.centerX().toFloat() / bitmap.width

                when {
                    centerX < 0.35f -> "left"
                    centerX <= 0.65f -> "ahead"
                    else -> "right"
                }
            }

            // Build spatial sentence
            val sentenceParts = mutableListOf<String>()

            val aheadObjects = spatialBuckets["ahead"]
            val pathStatus = if (aheadObjects.isNullOrEmpty()) {
                "Path is clear ahead."
            } else {
                val objectNames = aheadObjects.take(2).map { it.categories.firstOrNull()?.label ?: "object" }
                val objectText = if (objectNames.size == 1) objectNames[0] else "${objectNames.joinToString(" and ")}"
                "Path is blocked by $objectText ahead."
            }

            spatialBuckets["left"]?.let { objects ->
                if (objects.isNotEmpty()) {
                    val objectNames = objects.take(2).map { it.categories.firstOrNull()?.label ?: "object" }
                    val objectText = if (objectNames.size == 1) objectNames[0] else "${objectNames.joinToString(" and ")}"
                    sentenceParts.add("$objectText on your left")
                }
            }

            spatialBuckets["right"]?.let { objects ->
                if (objects.isNotEmpty()) {
                    val objectNames = objects.take(2).map { it.categories.firstOrNull()?.label ?: "object" }
                    val objectText = if (objectNames.size == 1) objectNames[0] else "${objectNames.joinToString(" and ")}"
                    sentenceParts.add("$objectText on your right")
                }
            }

            val finalSentence = if (sentenceParts.isEmpty()) {
                pathStatus
            } else {
                "$pathStatus Also, there is ${sentenceParts.joinToString(", ")}."
            }
            
            finalSentence
        } catch (e: Exception) {
            Log.e(TAG, "OfflineObjectDetector: Detection error", e)
            "I don't see any distinct objects in front of you."
        }
    }

    /**
     * Detect obstacles (hazardous objects) in the given bitmap.
     * @param bitmap The image to analyze
     * @return Description of detected obstacles
     */
    fun detectObstacles(bitmap: Bitmap): String {
        if (!isInitialized || detector == null) {
            Log.w(TAG, "OfflineObjectDetector: Detector not initialized")
            return "I don't see any obstacles in front of you."
        }

        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val detections = detector?.detect(tensorImage) ?: emptyList()

            Log.d(TAG, "OfflineObjectDetector: Detected ${detections.size} potential obstacles")

            // Filter for common obstacle categories
            val obstacleKeywords = listOf("chair", "table", "desk", "obstacle", "barrier", "wall", "door", "stair", "step")
            val obstacles = detections.filter { detection ->
                val label = detection.categories.firstOrNull()?.label?.lowercase() ?: ""
                obstacleKeywords.any { label.contains(it) }
            }

            if (obstacles.isEmpty()) {
                return "Path is clear. I don't see any obstacles in front of you."
            }

            val obstacleNames = obstacles.take(3).map { it.categories.firstOrNull()?.label ?: "obstacle" }
            "Path is blocked. There is ${obstacleNames.joinToString(" and ")} in front of you."
        } catch (e: Exception) {
            Log.e(TAG, "OfflineObjectDetector: Obstacle detection error", e)
            "I don't see any obstacles in front of you."
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        detector = null
        isInitialized = false
        Log.d(TAG, "OfflineObjectDetector: Released")
    }
}
