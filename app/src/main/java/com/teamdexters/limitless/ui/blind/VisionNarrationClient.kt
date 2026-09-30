package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.teamdexters.limitless.BuildConfig
import com.teamdexters.limitless.util.NetworkStatus
import com.teamdexters.limitless.util.NetworkStatusProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for Gemini Vision-based scene narration with offline fallback.
 * Handles image encoding, API calls, and graceful fallback to rule-based narration.
 */
object VisionNarrationClient {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    private const val TIMEOUT_MS = 5000L
    private const val MAX_IMAGE_EDGE = 1024
    private const val JPEG_QUALITY = 70

    private var networkStatusProvider: NetworkStatusProvider? = null

    /**
     * Register the network status provider for offline detection.
     * Called by BlindHomeScreen during initialization.
     */
    fun registerNetworkProvider(networkProvider: NetworkStatusProvider) {
        networkStatusProvider = networkProvider
    }

    // Temporary storage for latest frame (updated by BlindHomeScreen)
    private var latestFrame: Bitmap? = null
    private var latestRotation: Int = 0
    private var objectDetector: RealTimeObjectDetector? = null

    fun updateLatestFrame(frame: Bitmap, rotation: Int) {
        latestFrame = frame
        latestRotation = rotation
    }

    fun setObjectDetector(detector: RealTimeObjectDetector) {
        objectDetector = detector
    }

    private fun getLatestFrame(): Bitmap = latestFrame ?: throw IllegalStateException("No camera frame available")
    private fun getLatestRotation(): Int = latestRotation

    /**
     * Request a rich scene description from Gemini Vision.
     * Falls back to rule-based narration on any error, timeout, or offline status.
     *
     * @param bitmap Camera frame to analyze
     * @param rotationDegrees Frame rotation in degrees
     * @return Result<String> with Gemini response or fallback sentence
     */
    suspend fun describeScene(bitmap: Bitmap, rotationDegrees: Int = 0): Result<String> = withContext(Dispatchers.IO) {
        // Check network status
        val isOnline = networkStatusProvider?.isCurrentlyOnline() ?: false
        if (!isOnline) {
            return@withContext generateFallbackDescription(bitmap, rotationDegrees)
        }

        // Check API key
        val apiKey = BuildConfig.GEMINI_API_KEY.trim()
        if (apiKey.isEmpty()) {
            return@withContext generateFallbackDescription(bitmap, rotationDegrees)
        }

        // TODO(Naren): GeminiClient does not currently expose a vision method.
        // Implementing custom vision wrapper here until vision support is added to GeminiClient.

        try {
            // Encode image as base64 JPEG
            val base64Image = encodeImageToBase64(bitmap)

            // Build request with vision prompt
            val prompt = "You are Hazel, an accessibility assistant for a blind user. Describe this live camera view in 2 short, concise, natural sentences:\n1) Count and mention any people visible (e.g. 'a person sitting', '3 people ahead', 'a crowd of people').\n2) Mention key objects and their positions (e.g. 'laptop on a desk').\n3) Read any prominent text visible on screens, signs, or labels.\n4) Mention main colors.\nBe factual, calm, and direct. Do not say 'I see' or 'This image shows'."

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        }))
                    })
                ))
            }

            // Execute request with timeout
            val result = withTimeoutOrNull(TIMEOUT_MS) {
                executeGeminiVisionRequest(apiKey, requestJson)
            }

            if (result != null && result.isSuccess) {
                result
            } else {
                generateFallbackDescription(bitmap, rotationDegrees)
            }
        } catch (e: Exception) {
            generateFallbackDescription(bitmap, rotationDegrees)
        }
    }

    /**
     * Execute the Gemini Vision API request.
     */
    private suspend fun executeGeminiVisionRequest(apiKey: String, requestJson: JSONObject): Result<String> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$BASE_URL?key=$apiKey"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS.toInt()
                readTimeout = TIMEOUT_MS.toInt()
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val responseStringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    responseStringBuilder.append(line)
                }
                reader.close()

                val extractedText = parseGeminiResponse(responseStringBuilder.toString())
                if (extractedText.isNotBlank()) {
                    Result.success(extractedText)
                } else {
                    Result.failure(IllegalStateException("Empty text response from Gemini Vision API."))
                }
            } else {
                Result.failure(IllegalStateException("Gemini Vision API request failed with HTTP status code $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Encode bitmap to base64 JPEG with size constraints.
     */
    private fun encodeImageToBase64(bitmap: Bitmap): String {
        // Scale down if image is too large
        val scaledBitmap = if (bitmap.width > MAX_IMAGE_EDGE || bitmap.height > MAX_IMAGE_EDGE) {
            val scale = MAX_IMAGE_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        // Compress to JPEG
        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val imageBytes = outputStream.toByteArray()

        // Cleanup scaled bitmap if we created a new one
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }

    /**
     * Parse Gemini Vision response JSON.
     */
    private fun parseGeminiResponse(jsonString: String): String {
        return try {
            val jsonObject = JSONObject(jsonString)
            val candidates = jsonObject.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            if (parts.length() == 0) return ""
            val text = parts.getJSONObject(0).optString("text", "").trim()
            if (text.isNotEmpty()) text else fallbackRegexParse(jsonString)
        } catch (e: Throwable) {
            fallbackRegexParse(jsonString)
        }
    }

    private fun fallbackRegexParse(jsonString: String): String {
        val regex = Regex("\"text\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
        val match = regex.find(jsonString)
        return match?.groupValues?.get(1)?.replace("\\\"", "\"")?.trim() ?: ""
    }

    /**
     * Generate a rule-based description as fallback (Phase 1 style).
     * This is used when Gemini Vision is unavailable or fails.
     */
    private fun generateFallbackDescription(bitmap: Bitmap, rotationDegrees: Int): Result<String> {
        // Use Phase 1's object detection for fallback
        val detector = objectDetector
        if (detector != null && detector.isReady()) {
            val objects = detector.detectObjects(bitmap, rotationDegrees)
            if (objects.isNotEmpty()) {
                val topObject = objects.first()
                val frameWidth = bitmap.width
                val position = getPosition(topObject.boundingBox, frameWidth)
                val positionPhrase = when (position) {
                    "left" -> "on your left"
                    "center" -> "ahead"
                    "right" -> "on your right"
                    else -> "ahead"
                }
                val capitalizedLabel = topObject.label.substring(0, 1).uppercase() + topObject.label.substring(1)
                val description = "$capitalizedLabel $positionPhrase."
                return Result.success(description)
            }
        }

        // Ultimate fallback if no objects detected
        return Result.success("Scene description unavailable. No objects detected.")
    }

    /**
     * Determine the position of an object based on its bounding box center.
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
}
