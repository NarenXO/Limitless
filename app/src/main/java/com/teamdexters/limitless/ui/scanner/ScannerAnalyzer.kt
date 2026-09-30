package com.teamdexters.limitless.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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

data class CategorizedScanResult(
    val detectedObjects: List<String>,
    val personEmotion: String?,
    val lightingScoreText: String,
    val spokenVoiceSummary: String
)

class ScannerAnalyzer(private val context: Context) {
    
    private var objectDetector: ObjectDetector? = null
    private var liveDetections = mutableListOf<DetectedObject>()

    init {
        setupObjectDetector()
    }

    private fun setupObjectDetector() {
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
            
        objectDetector = try {
            ObjectDetector.createFromOptions(context, options)
        } catch (e: Throwable) {
            android.util.Log.e("LIMITLESS_SCANNER", "MediaPipe TFLite init failed, falling back to ML Kit: ${e.localizedMessage}")
            null
        }
    }

    fun detectObjectsLive(bitmap: Bitmap): List<DetectedObject> {
        if (objectDetector == null) {
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val labeler = com.google.mlkit.vision.label.ImageLabeling.getClient(
                com.google.mlkit.vision.label.defaults.ImageLabelerOptions.DEFAULT_OPTIONS
            )
            labeler.process(image).addOnSuccessListener { labels ->
                val detections = mutableListOf<DetectedObject>()
                for (label in labels) {
                    val text = label.text.lowercase()
                    val conf = label.confidence
                    if (conf <= 0.5f) continue
                    val accType = when {
                        text.contains("ramp") || text.contains("wheelchair ramp") -> AccessibilityObjectType.RAMP
                        text.contains("stairs") || text.contains("staircase") -> AccessibilityObjectType.STAIRS
                        text.contains("handrail") || text.contains("railing") -> AccessibilityObjectType.HANDRAIL
                        text.contains("door") || text.contains("doorway") || text.contains("entrance") -> AccessibilityObjectType.DOORWAY
                        else -> AccessibilityObjectType.OTHER
                    }
                    if (accType != AccessibilityObjectType.OTHER) {
                        detections.add(DetectedObject(text, conf, RectF(0f, 0f, 0f, 0f), accType))
                    }
                }
                liveDetections = detections
            }
            return liveDetections.toList()
        }

        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            objectDetector?.detectAsync(mpImage, System.currentTimeMillis())
        } catch (e: Throwable) {
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

    suspend fun analyzeCategorizedFrame(bitmap: Bitmap, rotationDegrees: Int): CategorizedScanResult = withContext(Dispatchers.IO) {
        val (objects, _, lighting) = analyzeFrame(bitmap, rotationDegrees)
        
        val faceDetected = detectFaces(bitmap, rotationDegrees)
        
        var emotion = "No Person in Scene"
        var cleanPersonState = "No person detected in scene"
        
        if (faceDetected) {
            val geminiClient = com.teamdexters.limitless.assistant.cloud.GeminiClient()
            val prompt = """
                Analyze this scene which contains a person.
                Detect the person's emotion/state.
                Return ONLY the emotion status like: "Friendly & Smiling", "Calm & Neutral", or "Focused & Attentive".
            """.trimIndent()
            
            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64Image = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
            
            try {
                val response = geminiClient.queryGemini(prompt, base64Image)
                if (response.isSuccess) {
                    val txt = response.getOrNull() ?: ""
                    if (txt.contains("Friendly", ignoreCase = true) || txt.contains("Smiling", ignoreCase = true)) {
                        emotion = "Friendly & Smiling"
                        cleanPersonState = "One person detected, appearing friendly and smiling"
                    } else if (txt.contains("Calm", ignoreCase = true) || txt.contains("Neutral", ignoreCase = true)) {
                        emotion = "Calm & Neutral"
                        cleanPersonState = "One person detected, appearing calm and neutral"
                    } else if (txt.contains("Focused", ignoreCase = true) || txt.contains("Attentive", ignoreCase = true)) {
                        emotion = "Focused & Attentive"
                        cleanPersonState = "One person detected, appearing focused and attentive"
                    } else {
                        emotion = "1 Person in Frame"
                        cleanPersonState = "One person detected"
                    }
                } else {
                    emotion = "1 Person in Frame"
                    cleanPersonState = "One person detected"
                }
            } catch(e: Exception) {
                emotion = "1 Person in Frame"
                cleanPersonState = "One person detected"
            }
        }
        
        val mappedObjects = objects.map { obj ->
            val label = obj.label.lowercase()
            val confStr = "(${(obj.confidence * 100).toInt()}%)"
            when {
                label.contains("laptop") || label.contains("computer") || label.contains("pc") || label.contains("keyboard") || label.contains("monitor") || label.contains("screen") -> "Laptop / PC $confStr"
                label.contains("guitar") || label.contains("piano") || label.contains("violin") || label.contains("flute") || label.contains("musical instrument") || label.contains("drum") -> "Musical Instrument $confStr"
                label.contains("desk") || label.contains("table") || label.contains("chair") || label.contains("bench") || label.contains("shelf") -> "Desk / Chair $confStr"
                label.contains("ramp") || label.contains("wheelchair ramp") -> "Wheelchair Ramp $confStr"
                label.contains("door") || label.contains("doorway") -> "Wide Doorway $confStr"
                label.contains("stairs") -> "Stairs $confStr"
                else -> "${obj.label.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }} $confStr"
            }
        }.distinct().take(5)
        
        val objectsVoice = if (mappedObjects.isNotEmpty()) mappedObjects.joinToString(", ") { it.substringBefore(" (") } else "None"
        
        val summary = "Scan complete. Objects detected: $objectsVoice. Person state: $cleanPersonState. Lighting condition is ${if (lighting > 60) "good" else "dim"}."
        
        CategorizedScanResult(
            detectedObjects = mappedObjects,
            personEmotion = emotion,
            lightingScoreText = if (lighting > 60) "Good Lighting ($lighting/100)" else "Dim Lighting ($lighting/100)",
            spokenVoiceSummary = summary
        )
    }

    suspend fun detectFaces(bitmap: Bitmap, rotationDegrees: Int): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        val faceDetectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        val faceDetector = FaceDetection.getClient(faceDetectorOptions)
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                continuation.resume(faces.isNotEmpty())
            }
            .addOnFailureListener {
                continuation.resume(false)
            }
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
