package com.teamdexters.limitless.blind

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.teamdexters.limitless.util.NetworkStatusTracker
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * AI invoker for Blind Assist with online/offline fallback.
 * Orchestrates voice command parsing, frame extraction, and AI response generation.
 */
object BlindAIInvoker {

    private const val TAG = "LIMITLESS_TRACE"
    private const val TIMEOUT_MS = 5000L

    private var networkStatusTracker: NetworkStatusTracker? = null
    private var geminiClient: GeminiClient? = null

    /**
     * Initialize the AI invoker with dependencies.
     * @param context Android context
     */
    fun initialize(context: Context) {
        networkStatusTracker = NetworkStatusTracker(context)
        networkStatusTracker?.register()
        geminiClient = GeminiClient()

        // Initialize offline detectors
        OfflineObjectDetector.initialize(context)

        // Initialize navigation voice handler
        BlindNavigationVoice.initialize(context)

        Log.d(TAG, "BlindAIInvoker: Initialized")
    }

    /**
     * Invoke vision AI with the given query.
     * @param context Android context
     * @param query Spoken text query from user
     * @return AI response string
     */
    suspend fun invokeVisionAI(context: Context, query: String): String {
        // Parse query into intent
        val intent = BlindVoiceCommandHandler.parseQuery(query)
        val prompt = BlindVoiceCommandHandler.getPromptForIntent(intent)

        // Check online status
        val isOnline = networkStatusTracker?.isCurrentlyOnline() ?: false

        Log.d(TAG, "BlindAIInvoker: Processed query='$query' intent=$intent online=$isOnline")

        // Handle navigation intents separately
        when (intent) {
            BlindVoiceCommandHandler.BlindIntent.START_NAVIGATION -> {
                val destination = BlindNavigationVoice.extractDestination(query) ?: "unknown location"
                return BlindNavigationVoice.startNavigation(destination)
            }
            BlindVoiceCommandHandler.BlindIntent.TELL_ROUTE -> {
                return BlindNavigationVoice.tellRoute()
            }
            BlindVoiceCommandHandler.BlindIntent.STOP_NAVIGATION -> {
                return BlindNavigationVoice.stopNavigation()
            }
            else -> {
                // Continue with vision AI for other intents
            }
        }

        // --- 100% REAL-TIME LOCAL SDK OVERRIDES ---
        // Force Text and Color to use local ML Kit & Palette immediately, skipping cloud delay.
        if (intent == BlindVoiceCommandHandler.BlindIntent.READ_TEXT) {
            val (frame, rotation) = CameraFrameManager.getLatestFrame()
            val result = OfflineTextReader.readText(frame, rotation)
            Log.d(TAG, "BlindAIInvoker: Executed REAL-TIME OCR result='$result'")
            return result
        }

        if (intent == BlindVoiceCommandHandler.BlindIntent.WHAT_COLOR) {
            val (frame, _) = CameraFrameManager.getLatestFrame()
            val result = OfflineColorDetector.detectColor(frame)
            Log.d(TAG, "BlindAIInvoker: Executed REAL-TIME Color result='$result'")
            return result
        }

        // Get latest frame (Phase 3: will convert to Base64 for vision API)
        val (frame, rotation) = CameraFrameManager.getLatestFrame()

        // Check if we're using the fallback bitmap (camera not ready yet)
        val isFallbackFrame = (frame.width == 640 && frame.height == 480)
        if (isFallbackFrame) {
            Log.w(TAG, "BlindAIInvoker: Using fallback bitmap (camera not ready yet)")
        }

        // Try online Gemini API if available
        if (isOnline) {
            val response = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    // Get Base64 image for Gemini Vision API (optimized with 50% JPEG quality)
                    val (base64Image, _) = CameraFrameManager.getLatestFrameAsBase64()
                    Log.d(TAG, "BlindAIInvoker: Sending Base64 frame to Gemini (size=${base64Image.length})")
                    val scenePrompt = "You are Hazel, an accessibility assistant for a blind user. Describe this live camera view in 2 short, concise, natural sentences:\n1) Count and mention any people visible (e.g. 'a person sitting', '3 people ahead', 'a crowd of people').\n2) Mention key objects and their positions (e.g. 'laptop on a desk').\n3) Read any prominent text visible on screens, signs, or labels.\n4) Mention main colors.\nBe factual, calm, and direct. Do not say 'I see' or 'This image shows'."
                    val result = geminiClient?.queryGemini(scenePrompt, base64Image)
                    result?.getOrNull() ?: ""
                } catch (e: Exception) {
                    Log.e(TAG, "BlindAIInvoker: Gemini API error", e)
                    null
                }
            }

            if (!response.isNullOrBlank()) {
                Log.d(TAG, "BlindAIInvoker: Gemini response received")
                return response
            } else {
                Log.w(TAG, "BlindAIInvoker: Gemini timeout or error, falling back to offline")
            }
        }

        // Offline fallback
        Log.d(TAG, "BlindAIInvoker: Using offline fallback")
        return getOfflineFallbackResponse(intent, frame)
    }

    /**
     * Get offline fallback response for the given intent.
     * Uses offline detectors: TFLite object detection, ML Kit OCR, Palette color detection.
     * @param intent The detected intent
     * @param frame The camera frame to analyze
     * @return Fallback response string
     */
    private suspend fun getOfflineFallbackResponse(intent: BlindVoiceCommandHandler.BlindIntent, frame: android.graphics.Bitmap): String {
        return when (intent) {
            BlindVoiceCommandHandler.BlindIntent.DESCRIBE_SURROUNDINGS,
            BlindVoiceCommandHandler.BlindIntent.WHATS_IN_FRONT,
            BlindVoiceCommandHandler.BlindIntent.UNKNOWN -> {
                coroutineScope {
                    val objectsJob = async { OfflineObjectDetector.detectObjects(frame) }
                    val textJob = async { OfflineTextReader.readTextRaw(frame) }
                    val colorJob = async { OfflineColorDetector.detectColorRaw(frame) }

                    val objectsResult = objectsJob.await()
                    val textResult = textJob.await()
                    val colorResult = colorJob.await()

                    val sb = StringBuilder()
                    if (objectsResult.isNotBlank()) {
                        sb.append(objectsResult)
                        sb.append(" ")
                    } else {
                        sb.append("A dark surface ahead. ")
                    }

                    if (colorResult != null) {
                        sb.append("Dominant color is $colorResult. ")
                    }

                    if (textResult != null) {
                        // only take the top 2 lines of text
                        val lines = textResult.split("\n").take(2).joinToString(" ")
                        sb.append("Text visible: '$lines'.")
                    }

                    val finalResult = sb.toString().trim()
                    Log.d(TAG, "OfflineAI: Executed Super Vision Engine for intent=$intent result='$finalResult'")
                    finalResult
                }
            }

            BlindVoiceCommandHandler.BlindIntent.READ_TEXT -> {
                val result = OfflineTextReader.readText(frame)
                Log.d(TAG, "OfflineAI: Executed fallback for intent=$intent result='$result'")
                result
            }

            BlindVoiceCommandHandler.BlindIntent.DETECT_OBSTACLES -> {
                val result = OfflineObjectDetector.detectObstacles(frame)
                Log.d(TAG, "OfflineAI: Executed fallback for intent=$intent result='$result'")
                result
            }

            BlindVoiceCommandHandler.BlindIntent.WHAT_COLOR -> {
                val result = OfflineColorDetector.detectColor(frame)
                Log.d(TAG, "OfflineAI: Executed fallback for intent=$intent result='$result'")
                result
            }

            else -> {
                val result = OfflineObjectDetector.detectObjects(frame)
                val finalResult = if (result.isBlank()) "I don't see any distinct objects in front of you." else result
                Log.d(TAG, "OfflineAI: Executed fallback for intent=$intent result='$finalResult'")
                finalResult
            }
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        networkStatusTracker?.unregister()
        networkStatusTracker = null
        geminiClient = null
        OfflineObjectDetector.release()
        OfflineTextReader.release()
        BlindNavigationVoice.release()
        Log.d(TAG, "BlindAIInvoker: Released")
    }
}
