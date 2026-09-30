package com.teamdexters.limitless.blind

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.teamdexters.limitless.util.NetworkStatusTracker
import kotlinx.coroutines.withTimeoutOrNull

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

        // Get latest frame (Phase 3: will convert to Base64 for vision API)
        val (frame, rotation) = CameraFrameManager.getLatestFrame()

        if (frame == null) {
            Log.w(TAG, "BlindAIInvoker: No frame available, using text-only fallback")
            return "I don't have a camera frame to analyze."
        }

        // Try online Gemini API if available
        if (isOnline) {
            val response = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    // Get Base64 image for Gemini Vision API (optimized with 50% JPEG quality)
                    val (base64Image, _) = CameraFrameManager.getLatestFrameAsBase64()
                    val result = geminiClient?.queryGemini(prompt, base64Image)
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
            BlindVoiceCommandHandler.BlindIntent.WHATS_IN_FRONT -> {
                val result = OfflineObjectDetector.detectObjects(frame)
                Log.d(TAG, "OfflineAI: Executed fallback for intent=$intent result='$result'")
                result
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

            BlindVoiceCommandHandler.BlindIntent.UNKNOWN -> {
                val result = OfflineObjectDetector.detectObjects(frame)
                Log.d(TAG, "OfflineAI: Executed fallback for intent=$intent result='$result'")
                result
            }
            else -> {
                "Command not supported in offline mode."
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
        OfflineColorDetector.release()
        BlindNavigationVoice.release()
        Log.d(TAG, "BlindAIInvoker: Released")
    }
}
