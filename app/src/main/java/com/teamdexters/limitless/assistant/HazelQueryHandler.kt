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
    private val geminiClient: GeminiClient = GeminiClient(),
    private val networkStatusTracker: NetworkStatusProvider? = null
) {
    companion object {
        private const val TAG = "HazelQueryHandler"
        const val OFFLINE_FALLBACK_MESSAGE = "I couldn't understand that. Try again or use the app manually."
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
            val isOnline = isNetworkAvailable()
            val responseText = if (isOnline && isApiKeyPresent()) {
                val result = geminiClient.queryGemini(rawQuery)
                result.getOrElse { e ->
                    Log.w(TAG, "Gemini API query failed or timed out: ${e.message}. Falling back to offline message.")
                    OFFLINE_FALLBACK_MESSAGE
                }
            } else {
                Log.i(TAG, "Device is offline or Gemini API key is missing. Using offline fallback.")
                OFFLINE_FALLBACK_MESSAGE
            }

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
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

    private fun speakResponse(tts: TextToSpeech?, text: String) {
        try {
            tts?.language = Locale.US
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hazel_response_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech speaking error: ${e.message}", e)
        }
    }
}
