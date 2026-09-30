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

        // Get latest frame (Phase 3: will convert to Base64 for vision API)
        val (frame, rotation) = CameraFrameManager.getLatestFrame()

        if (frame == null) {
            Log.w(TAG, "BlindAIInvoker: No frame available, using text-only fallback")
            return getOfflineFallbackResponse(intent)
        }

        // Try online Gemini API if available
        if (isOnline) {
            val response = withTimeoutOrNull(TIMEOUT_MS) {
                try {
                    // Phase 3: Add image data to Gemini request
                    // For now, use text-only prompt as fallback
                    val result = geminiClient?.queryGemini(prompt)
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
        return getOfflineFallbackResponse(intent)
    }

    /**
     * Get offline fallback response for the given intent.
     * Stubs for Phase 3 implementation.
     * @param intent The detected intent
     * @return Fallback response string
     */
    private fun getOfflineFallbackResponse(intent: BlindVoiceCommandHandler.BlindIntent): String {
        return when (intent) {
            BlindVoiceCommandHandler.BlindIntent.DESCRIBE_SURROUNDINGS ->
                "I'm offline. Please connect to the internet for scene description."

            BlindVoiceCommandHandler.BlindIntent.WHATS_IN_FRONT ->
                "I'm offline. Please connect to the internet to describe what's in front."

            BlindVoiceCommandHandler.BlindIntent.READ_TEXT ->
                "I'm offline. Please connect to the internet to read text."

            BlindVoiceCommandHandler.BlindIntent.DETECT_OBSTACLES ->
                "I'm offline. Please connect to the internet to detect obstacles."

            BlindVoiceCommandHandler.BlindIntent.WHAT_COLOR ->
                "I'm offline. Please connect to the internet to identify colors."

            BlindVoiceCommandHandler.BlindIntent.UNKNOWN ->
                "I'm offline. Please connect to the internet for assistance."
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        networkStatusTracker?.unregister()
        networkStatusTracker = null
        geminiClient = null
        Log.d(TAG, "BlindAIInvoker: Released")
    }
}
