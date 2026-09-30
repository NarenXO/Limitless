package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions
import java.io.IOException

/**
 * Path feature detector for ramps, stairs, curbs, zebra crossings, and traffic lights.
 * Reuses MediaPipe Object Detector where labels overlap, with fallback to placeholder model.
 * Runs at 1000ms interval to save battery.
 */
class PathFeatureDetectorV2(private val context: Context) {

    companion object {
        private const val PATH_MODEL_ASSET = "blind/path_features.tflite"
        private const val MIN_CONFIDENCE = 0.55f
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f
        private const val COOLDOWN_MS = 8000L // 8 seconds
    }

    private var detector: ObjectDetector? = null
    private var isInitialized = false
    private var isUsingPlaceholder = false

    // Cooldown tracking
    private val lastSpokenFeatures = mutableMapOf<String, Long>()
    private val lastConfidence = mutableMapOf<String, Float>()

    /**
     * Initialize the path feature detector.
     * Tries to load the dedicated path features model first.
     * Falls back to the main object detector if not available.
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(mainObjectDetector: ObjectDetector?): Boolean {
        // Try to load dedicated path features model
        try {
            val modelFile = FileUtil.loadMappedFile(context, PATH_MODEL_ASSET)

            // Check if the file is a valid TFLite model (not a placeholder)
            val isValidModel = modelFile.capacity() > 1000 // Placeholder files are typically small

            if (isValidModel) {
                val options = ObjectDetectorOptions.builder()
                    .setMaxResults(5)
                    .setScoreThreshold(MIN_CONFIDENCE)
                    .build()

                detector = ObjectDetector.createFromBufferAndOptions(modelFile, options)
                isInitialized = true
                isUsingPlaceholder = false
                return true
            }
        } catch (_: IOException) {
            // Model file not found, fall back to main detector
        } catch (_: Exception) {
            // Other initialization errors, fall back to main detector
        }

        // Fallback: use the main object detector if available
        if (mainObjectDetector != null) {
            detector = mainObjectDetector
            isInitialized = true
            isUsingPlaceholder = true
            return true
        }

        isInitialized = false
        return false
    }

    /**
     * Detect path features in the given bitmap.
     * @param bitmap The image to analyze
     * @param rotationDegrees The rotation of the image in degrees
     * @param frameWidth Width of the camera frame for position calculation
     * @param frameHeight Height of the camera frame for distance estimation
     * @param ttsManager TTS manager for speaking warnings
     * @param scope Coroutine scope for async operations
     * @return List of detected path features
     */
    fun detectPathFeatures(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        frameWidth: Int,
        frameHeight: Int,
        ttsManager: TTSManager,
        scope: CoroutineScope
    ): List<PathFeature> {
        if (!isInitialized || detector == null) {
            return emptyList()
        }

        return try {
            // Handle rotation before passing to detector
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

            // Filter by confidence >= 0.55 and map to PathFeature
            val filteredResults = results.mapNotNull { detection ->
                val confidence = detection.categories.firstOrNull()?.score ?: 0f
                if (confidence >= MIN_CONFIDENCE) {
                    val boundingBoxRect = Rect()
                    detection.boundingBox.round(boundingBoxRect)

                    val label = normalizeLabel(detection.categories.firstOrNull()?.label ?: "unknown")
                    val featureType = mapLabelToFeatureType(label)

                    if (featureType != null) {
                        PathFeature(
                            type = featureType,
                            confidence = confidence,
                            boundingBox = boundingBoxRect,
                            label = label
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

            // Filter by cooldown and speak warnings
            val currentTime = System.currentTimeMillis()
            val featuresToAnnounce = mutableListOf<PathFeature>()

            for (feature in filteredResults) {
                val featureKey = "${feature.type}_${feature.label}"
                val lastSpoken = lastSpokenFeatures[featureKey]
                val lastConf = lastConfidence[featureKey] ?: 0f

                // Announce if:
                // 1. Never spoken, OR
                // 2. Spoken more than 8 seconds ago, OR
                // 3. Confidence jumped significantly (>= 0.75 and increased by 0.15)
                val shouldAnnounce = when {
                    lastSpoken == null -> true
                    (currentTime - lastSpoken) > COOLDOWN_MS -> true
                    (feature.confidence >= HIGH_CONFIDENCE_THRESHOLD && (feature.confidence - lastConf) >= 0.15f) -> true
                    else -> false
                }

                if (shouldAnnounce) {
                    featuresToAnnounce.add(feature)
                    lastSpokenFeatures[featureKey] = currentTime
                    lastConfidence[featureKey] = feature.confidence

                    // Speak warning
                    val warning = generateWarning(feature, frameWidth, frameHeight)
                    scope.launch {
                        ttsManager.speak(warning)
                    }
                }
            }

            // Clean up old entries from cooldown map
            lastSpokenFeatures.entries.removeIf { (_, timestamp) ->
                currentTime - timestamp > COOLDOWN_MS
            }

            filteredResults
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Normalize raw model labels to path feature types.
     */
    private fun normalizeLabel(label: String): String {
        return when (label.lowercase()) {
            "stairs", "staircase", "steps" -> "stairs"
            "ramp", "wheelchair ramp" -> "ramp"
            "curb", "curb ramp" -> "curb"
            "zebra crossing", "crosswalk", "pedestrian crossing" -> "zebra crossing"
            "traffic light", "traffic signal", "stop light" -> "traffic light"
            else -> label.lowercase()
        }
    }

    /**
     * Map normalized label to PathFeatureType.
     */
    private fun mapLabelToFeatureType(label: String): PathFeatureType? {
        return when (label) {
            "stairs" -> PathFeatureType.STAIRS
            "ramp" -> PathFeatureType.RAMP
            "curb" -> PathFeatureType.CURB
            "zebra crossing" -> PathFeatureType.ZEBRA_CROSSING
            "traffic light" -> PathFeatureType.TRAFFIC_LIGHT
            else -> null
        }
    }

    /**
     * Generate a spoken warning for a detected path feature.
     */
    private fun generateWarning(feature: PathFeature, frameWidth: Int, frameHeight: Int): String {
        val position = getPosition(feature.boundingBox, frameWidth)
        val distance = estimateDistance(feature.boundingBox, frameWidth, frameHeight)

        return when (feature.type) {
            PathFeatureType.STAIRS -> "Warning: stairs ahead, about $distance."
            PathFeatureType.RAMP -> "Ramp ahead ${positionPhrase(position)}."
            PathFeatureType.CURB -> "Curb detected ahead, step down carefully."
            PathFeatureType.ZEBRA_CROSSING -> "Zebra crossing ahead."
            PathFeatureType.TRAFFIC_LIGHT -> "Traffic light ahead${if (feature.label.contains("red", ignoreCase = true)) ", currently red" else ""}."
        }
    }

    /**
     * Determine the position of a feature based on its bounding box center.
     */
    private fun getPosition(boundingBox: Rect, frameWidth: Int): String {
        val centerX = boundingBox.centerX().toFloat()
        val relativeX = centerX / frameWidth

        return when {
            relativeX < 0.35f -> "left"
            relativeX <= 0.65f -> "center"
            else -> "right"
        }
    }

    /**
     * Generate position phrase for speech.
     */
    private fun positionPhrase(position: String): String {
        return when (position) {
            "left" -> "on your left"
            "center" -> "ahead"
            "right" -> "on your right"
            else -> "ahead"
        }
    }

    /**
     * Estimate distance based on bounding box size.
     */
    private fun estimateDistance(boundingBox: Rect, frameWidth: Int, frameHeight: Int): String {
        val frameArea = frameWidth * frameHeight
        val bboxArea = boundingBox.width() * boundingBox.height()
        val relativeSize = bboxArea.toFloat() / frameArea

        return when {
            relativeSize > 0.4f -> "1 meter"
            relativeSize > 0.25f -> "2 meters"
            relativeSize > 0.15f -> "3 meters"
            else -> "5 meters"
        }
    }

    /**
     * Check if the detector is ready to use.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Check if using placeholder (main object detector).
     */
    fun isUsingPlaceholderModel(): Boolean = isUsingPlaceholder

    /**
     * Close the detector and release resources.
     */
    fun close() {
        // Don't close if we're using the main object detector (shared instance)
        if (!isUsingPlaceholder) {
            detector?.close()
        }
        detector = null
        isInitialized = false
    }
}

/**
 * Data class representing a detected path feature.
 */
data class PathFeature(
    val type: PathFeatureType,
    val confidence: Float,
    val boundingBox: Rect,
    val label: String
) {
    constructor(
        label: String,
        confidence: Float,
        boundingBox: Rect
    ) : this(type = PathFeatureType.RAMP, confidence = confidence, boundingBox = boundingBox, label = label)
}

/**
 * Enum representing path feature types.
 */
enum class PathFeatureType {
    STAIRS,
    RAMP,
    CURB,
    ZEBRA_CROSSING,
    TRAFFIC_LIGHT
}
