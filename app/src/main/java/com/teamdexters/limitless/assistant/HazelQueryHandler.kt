package com.teamdexters.limitless.assistant

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.util.Log
import com.teamdexters.limitless.BuildConfig
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Handles unmatched [HazelIntent.GeneralQuery] inputs by attempting optional Gemini cloud queries
 * when online, or falling back gracefully to offline accessibility responses when offline.
 *
 * Enforces 100% offline-first design rules: never crashes or hangs when offline or unauthenticated.
 */
class HazelQueryHandler(
    private val context: Context,
    private val geminiClient: GeminiClient = GeminiClient()
) {
    companion object {
        private const val TAG = "HazelQueryHandler"
        const val OFFLINE_FALLBACK_MESSAGE = "I couldn't understand that. Try again or use the app manually."
    }

    /**
     * Processes a general query by checking network availability and triggering
     * Gemini or the offline fallback message.
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
            val responseText = if (isNetworkAvailable() && isApiKeyPresent()) {
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
     * Checks whether active network interface has internet capability.
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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
