package com.teamdexters.limitless.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.teamdexters.limitless.BuildConfig
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.teamdexters.limitless.util.NetworkStatus
import com.teamdexters.limitless.util.NetworkStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Handles unmatched [HazelIntent.GeneralQuery] inputs by attempting optional Gemini cloud queries
 * when online, or falling back gracefully to offline accessibility responses when offline.
 *
 * Uses [NetworkStatusTracker] for reactive, cached network checks instead of performing a
 * synchronous connectivity lookup on each query. This guarantees zero main-thread blocking
 * and immediate offline fallback in airplane mode.
 *
 * Enforces 100% offline-first design rules: never crashes or hangs when offline or unauthenticated.
 */
class HazelQueryHandler(
    private val context: Context,
    private val geminiClient: GeminiClient,
    private val networkStatusTracker: NetworkStatusProvider? = null
) {
    companion object {
        private const val TAG = "HazelQueryHandler"
    }

    /**
     * Processes a general query by checking network availability via [NetworkStatusTracker]
     * and triggering Gemini or the offline fallback message.
     *
     * Network check priority:
     * 1. [NetworkStatusTracker] reactive cached status (preferred, zero-cost).
     * 2. Fallback: synchronous [isNetworkAvailableFallback] if tracker is null.
     *
     * @param rawQuery The user's unmatched spoken input text.
     * @param scope CoroutineScope for asynchronous execution.
     * @param tts TextToSpeech instance to speak the result.
     * @param onResponseReady Callback with response text for UI subtitle mirroring.
     */
    fun handleGeneralQuery(
        rawQuery: String,
        scope: CoroutineScope,
        tts: TextToSpeech?,
        onResponseReady: (String) -> Unit
    ) {
        scope.launch {
            val result = geminiClient.queryGemini(rawQuery)
            val responseText = result.getOrDefault("I'm sorry, I couldn't process that right now. How can I help you?")

            withContext(Dispatchers.Main) {
                onResponseReady(responseText)
                speakResponse(tts, responseText)
            }
        }
    }

    /**
     * Checks whether network is available using the reactive [NetworkStatusTracker] first,
     * falling back to a synchronous connectivity check if tracker is not provided.
     */
    fun isNetworkAvailable(): Boolean {
        // Prefer reactive tracker (zero-cost cached check)
        networkStatusTracker?.let { tracker ->
            return tracker.statusFlow.value is NetworkStatus.Online
        }

        // Fallback: synchronous ConnectivityManager check
        return isNetworkAvailableFallback()
    }

    /**
     * Fallback synchronous connectivity check using ConnectivityManager.
     * Used only when [NetworkStatusTracker] is not injected.
     */
    private fun isNetworkAvailableFallback(): Boolean {
        return try {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork
            if (activeNetwork != null) {
                val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                if (capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    return true
                }
            }
            @Suppress("DEPRECATION")
            connectivityManager?.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks whether a valid BuildConfig.GEMINI_API_KEY is configured.
     */
    fun isApiKeyPresent(): Boolean {
        return BuildConfig.GEMINI_API_KEY.trim().isNotEmpty()
    }

    fun handleVisionQuery(
        query: String,
        scope: CoroutineScope,
        tts: TextToSpeech?,
        onResponseReady: (String) -> Unit
    ) {
        scope.launch {
            val isOnline = isNetworkAvailable()
            val offlineFallback = "I'm having trouble connecting right now. You can try asking me to open a specific tool like the Scanner or Community map."
            
            val responseText = if (isOnline && isApiKeyPresent()) {
                val latestFrame = com.teamdexters.limitless.assistant.vision.CameraFrameManager.getFrame()
                if (latestFrame != null) {
                    val base64Image = bitmapToBase64(latestFrame)
                    val result = geminiClient.queryGemini(query, base64Image)
                    result.getOrElse { e ->
                        Log.w(TAG, "Gemini Vision query failed: ${e.message}")
                        "I had trouble analyzing the image. Please try again."
                    }
                } else {
                    "My camera isn't active right now, so I can't see anything."
                }
            } else {
                offlineFallback
            }

            withContext(Dispatchers.Main) {
                onResponseReady(responseText)
                speakResponse(tts, responseText)
            }
        }
    }

    private fun bitmapToBase64(bitmap: android.graphics.Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, outputStream)
        return android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private fun speakResponse(tts: TextToSpeech?, text: String) {
        try {
            Log.d("LIMITLESS_TRACE", "[Hazel] Speaking response aloud: $text")
            tts?.language = Locale.US
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HAZEL_SPEECH_ID")
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech speaking error: ${e.message}", e)
        }
    }
}
