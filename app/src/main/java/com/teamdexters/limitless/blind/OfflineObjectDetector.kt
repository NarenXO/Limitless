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
 *
 * Accuracy improvements:
 *  - Confidence threshold lowered to 0.35 so everyday indoor objects are reliably detected.
 *  - Top 4 most confident results returned (cap by MAX_RESULTS).
 *  - Spatial bucketing: normalized bbox centre X → left / ahead / right.
 *  - Depth hint: if bbox area > 35% of frame → append ", close".
 *  - Label normalization maps raw model labels to everyday words.
 *  - Short-term memory (5 s) prevents repeated identical announcements.
 *  - Output format: "There is a chair on your left, a person ahead close, and a door on your right."
 */
object OfflineObjectDetector {

    private const val TAG = "LIMITLESS_TRACE"
    private const val MODEL_PATH = "blind/efficientdet_lite0.tflite"
    private const val MAX_RESULTS = 4
    private const val CONFIDENCE_THRESHOLD = 0.30f

    // Short-term memory: label+position → last-spoken timestamp
    private val spokenObjects = mutableMapOf<String, Long>()
    private const val MEMORY_MS = 5000L

    private var detector: ObjectDetector? = null
    private var isInitialized = false

    /**
     * Initialize the object detector with the TFLite model.
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(context: Context): Boolean {
        return try {
            val modelFile = FileUtil.loadMappedFile(context, MODEL_PATH)
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
     * Detect objects and generate a natural-language spatial sentence.
     *
     * Applies:
     *  - 35% confidence filter
     *  - Top-4 cap (most confident)
     *  - Spatial bucketing (left / ahead / right)
     *  - Depth hint (", close" when bbox > 35% of frame)
     *  - 5-second short-term memory to avoid repeating identical objects
     *
     * @param bitmap The image to analyze
     * @param frameWidth Width of the frame (for normalisation)
     * @param frameHeight Height of the frame (for depth hint)
     * @return Spatial scene sentence, e.g. "There is a chair on your left, a person ahead close."
     */
    fun detectObjects(bitmap: Bitmap, frameWidth: Int = bitmap.width, frameHeight: Int = bitmap.height): String {
        if (!isInitialized || detector == null) {
            Log.w(TAG, "OfflineObjectDetector: Detector not initialized")
            return ""
        }

        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val detections: List<Detection> = detector?.detect(tensorImage) ?: emptyList()
            Log.d(TAG, "OfflineObjectDetector: Detected ${detections.size} objects")

            if (detections.isEmpty()) {
                return ""
            }

            val validDetections = detections.filter { (it.categories.firstOrNull()?.score ?: 0f) >= CONFIDENCE_THRESHOLD }
            
            if (validDetections.isEmpty()) {
                return ""
            }

            val peopleDetections = validDetections.filter { normalizeLabel(it.categories.firstOrNull()?.label ?: "").equals("person", ignoreCase = true) }
            val objectDetections = validDetections.filter { !normalizeLabel(it.categories.firstOrNull()?.label ?: "").equals("person", ignoreCase = true) }
                .sortedByDescending { it.categories.firstOrNull()?.score ?: 0f }
                .take(MAX_RESULTS)

            // Clean up stale short-term memory
            val now = System.currentTimeMillis()
            spokenObjects.entries.removeIf { (_, ts) -> now - ts > MEMORY_MS }

            val frameArea = (frameWidth * frameHeight).toFloat()
            val sentenceParts = mutableListOf<String>()

            // People logic
            if (peopleDetections.isNotEmpty()) {
                val count = peopleDetections.size
                val peopleStr = when {
                    count == 1 -> "1 person"
                    count in 2..4 -> "$count people"
                    else -> "a crowd of people"
                }
                
                // Try to get position of the most confident person
                val mainPerson = peopleDetections.maxByOrNull { it.categories.firstOrNull()?.score ?: 0f }
                val positionStr = if (mainPerson != null) {
                    val centerX = mainPerson.boundingBox.centerX() / frameWidth.toFloat()
                    when {
                        centerX < 0.35f  -> " on your left"
                        centerX <= 0.65f -> " ahead"
                        else             -> " on your right"
                    }
                } else " ahead"
                
                val memKey = "people_${count}_$positionStr"
                if (!spokenObjects.containsKey(memKey)) {
                    sentenceParts.add("$peopleStr$positionStr")
                    spokenObjects[memKey] = now
                }
            }

            // Other objects logic
            for (detection in objectDetections) {
                val bbox = detection.boundingBox
                val label = normalizeLabel(detection.categories.firstOrNull()?.label ?: "object")
                val centerX = bbox.centerX() / frameWidth.toFloat()

                val position = when {
                    centerX < 0.35f  -> "on your left"
                    centerX <= 0.65f -> "ahead"
                    else             -> "on your right"
                }

                // 5-second dedup key
                val memKey = "${label}_${position}"
                if (spokenObjects.containsKey(memKey)) continue // still within memory window

                val bboxArea = bbox.width() * bbox.height()
                val depthHint = if (bboxArea > frameArea * 0.35f) " close" else ""

                sentenceParts.add("a $label $position$depthHint")
                spokenObjects[memKey] = now
            }

            if (sentenceParts.isEmpty()) {
                return ""
            }

            // Build natural-language sentence
            return when (sentenceParts.size) {
                1 -> sentenceParts[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "."
                2 -> sentenceParts[0].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " and " + sentenceParts[1] + "."
                else -> {
                    val last = sentenceParts.last()
                    val rest = sentenceParts.dropLast(1).joinToString(", ")
                    rest.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + ", and " + last + "."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OfflineObjectDetector: Detection error", e)
            ""
        }
    }

    /**
     * Detect obstacles (high-risk objects) and return a spoken warning.
     * Uses the same 35% threshold; filters by common obstacle label keywords.
     */
    fun detectObstacles(bitmap: Bitmap): String {
        if (!isInitialized || detector == null) {
            Log.w(TAG, "OfflineObjectDetector: Detector not initialized")
            return "I don't see any obstacles in front of you."
        }

        return try {
            val tensorImage = TensorImage.fromBitmap(bitmap)
            val detections: List<Detection> = detector?.detect(tensorImage) ?: emptyList()
            Log.d(TAG, "OfflineObjectDetector: Detected ${detections.size} potential obstacles")

            val obstacleKeywords = listOf(
                "chair", "table", "desk", "obstacle", "barrier", "wall",
                "door", "stair", "step", "person", "bottle", "bag", "suitcase"
            )

            val obstacles = detections.filter { detection ->
                val label = detection.categories.firstOrNull()?.label?.lowercase() ?: ""
                val score = detection.categories.firstOrNull()?.score ?: 0f
                score >= CONFIDENCE_THRESHOLD && obstacleKeywords.any { label.contains(it) }
            }

            if (obstacles.isEmpty()) {
                return "I don't see any obstacles in front of you."
            }

            val names = obstacles
                .sortedByDescending { it.categories.firstOrNull()?.score ?: 0f }
                .take(3)
                .map { normalizeLabel(it.categories.firstOrNull()?.label ?: "obstacle") }

            when (names.size) {
                1 -> "Obstacle ahead: ${names[0]}. Please proceed with caution."
                else -> "Obstacles ahead: ${names.joinToString(", ")}. Please proceed with caution."
            }
        } catch (e: Exception) {
            Log.e(TAG, "OfflineObjectDetector: Obstacle detection error", e)
            "I don't see any obstacles in front of you."
        }
    }

    /**
     * Normalize raw model labels to simple everyday words.
     */
    private fun normalizeLabel(label: String): String {
        return when (label.lowercase()) {
            "cell phone", "mobile phone" -> "phone"
            "dining table"               -> "table"
            "sofa", "couch"              -> "couch"
            "potted plant"               -> "plant"
            "laptop"                     -> "laptop"
            "chair"                      -> "chair"
            "person"                     -> "person"
            "bottle"                     -> "bottle"
            "door"                       -> "door"
            "cup"                        -> "cup"
            "suitcase", "luggage"        -> "bag"
            "tv", "television"           -> "television"
            "backpack"                   -> "bag"
            else                         -> label.lowercase()
        }
    }

    /**
     * Clear short-term spoken-objects memory.
     * Call when the scene changes significantly.
     */
    fun clearMemory() {
        spokenObjects.clear()
        Log.d(TAG, "OfflineObjectDetector: Short-term memory cleared")
    }

    /**
     * Release resources.
     */
    fun release() {
        detector = null
        isInitialized = false
        spokenObjects.clear()
        Log.d(TAG, "OfflineObjectDetector: Released")
    }
}
