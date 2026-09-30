package com.teamdexters.limitless.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ScanObjectResult(val label: String, val confidence: Float)

enum class AccessibilityObjectType { RAMP, STAIRS, HANDRAIL, DOORWAY, OTHER }

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val category: AccessibilityObjectType
)

class ScannerAnalyzer(private val context: Context) {
    
    private var objectDetector: ObjectDetector? = null
    private var liveDetections = mutableListOf<DetectedObject>()

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("mediapipe/efficientdet_lite0.tflite")
                .build()
                
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setMaxResults(5)
                .setScoreThreshold(0.5f)
                .setResultListener { result, _ ->
                    val detections = mutableListOf<DetectedObject>()
                    for (detection in result.detections()) {
                        val category = detection.categories().firstOrNull() ?: continue
                        val label = category.categoryName().lowercase()
                        val conf = category.score()
                        if (conf <= 0.5f) continue
                        
                        val accType = when {
                            label.contains("ramp") || label.contains("wheelchair ramp") -> AccessibilityObjectType.RAMP
                            label.contains("stairs") || label.contains("staircase") -> AccessibilityObjectType.STAIRS
                            label.contains("handrail") || label.contains("railing") -> AccessibilityObjectType.HANDRAIL
                            label.contains("door") || label.contains("doorway") || label.contains("entrance") -> AccessibilityObjectType.DOORWAY
                            else -> AccessibilityObjectType.OTHER
                        }
                        if (accType != AccessibilityObjectType.OTHER) {
                            detections.add(DetectedObject(label, conf, detection.boundingBox(), accType))
                        }
                    }
                    liveDetections = detections
                }
                .setErrorListener { error ->
                    android.util.Log.e("ScannerAnalyzer", "ObjectDetector error: ${error.message}")
                }
                .build()
                
            objectDetector = ObjectDetector.createFromOptions(context, options)
        } catch (e: Exception) {
            android.util.Log.e("ScannerAnalyzer", "Error setting up ObjectDetector", e)
        }
    }

    fun detectObjectsLive(bitmap: Bitmap): List<DetectedObject> {
        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            objectDetector?.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Exception) {
            android.util.Log.e("ScannerAnalyzer", "Error in detectAsync", e)
        }
        return liveDetections.toList()
    }

    suspend fun analyzeFrame(bitmap: Bitmap, rotationDegrees: Int): Triple<List<ScanObjectResult>, List<String>, Int> = withContext(Dispatchers.Default) {
        val objectsDeferred = async { detectObjects(bitmap, rotationDegrees) }
        val textDeferred = async { recognizeSignageText(bitmap, rotationDegrees) }
        val lightingDeferred = async { calculateLightingScore(bitmap) }
        
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
                continuation.resume(results)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
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
                continuation.resume(allText)
            }
            .addOnFailureListener {
                continuation.resume(emptyList())
            }
    }

    suspend fun calculateLightingScore(bitmap: Bitmap): Int = withContext(Dispatchers.Default) {
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
                
                val luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b).toInt()
                totalLuminance += luminance
                count++
            }
        }
        
        val avgLuminance = if (count > 0) (totalLuminance / count).toInt() else 0
        val score = (avgLuminance * 100) / 255
        score.coerceIn(0, 100)
    }
}
