package com.teamdexters.limitless.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.teamdexters.limitless.BuildConfig
import com.teamdexters.limitless.assistant.cloud.GroqChatClient
import com.teamdexters.limitless.assistant.cloud.GeminiClient
import com.teamdexters.limitless.util.NetworkStatus
import com.teamdexters.limitless.util.NetworkStatusProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Handles unmatched [HazelIntent.GeneralQuery] inputs by attempting optional Groq cloud queries
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
    private val groqChatClient: GroqChatClient,
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
            Log.d("LIMITLESS_TRACE", "Speech:\n$rawQuery\n↓\nIntent:\nGeneralQuery\n↓\nCloud LLM")
            val responseText = try {
                if (isNetworkAvailable() && isApiKeyPresent()) {
                    val result = groqChatClient.chatCompletion(rawQuery)
                    result.getOrElse { e ->
                        Log.e("LIMITLESS_TRACE", "Groq query failed: ${e.localizedMessage}", e)
                        getOfflineResponse(rawQuery)
                    }
                } else {
                    getOfflineResponse(rawQuery)
                }
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "Groq query failed: ${e.localizedMessage}", e)
                getOfflineResponse(rawQuery)
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
     * Checks whether a valid Groq API key is configured.
     */
    fun isApiKeyPresent(): Boolean {
        return groqChatClient.secureKeyProvider.getGroqApiKey().isNotEmpty()
    }

    /**
     * Checks whether a valid Gemini API key is configured (for vision queries).
     */
    fun isGeminiApiKeyPresent(): Boolean {
        return !geminiClient.secureKeyProvider.getGeminiKey().isNullOrEmpty()
    }

    fun handleVisionQuery(
        query: String,
        scope: CoroutineScope,
        tts: TextToSpeech?,
        onResponseReady: (String) -> Unit
    ) {
        scope.launch {
            val isOnline = isNetworkAvailable()

            val responseText = if (isOnline && isGeminiApiKeyPresent()) {
                val latestFrame = com.teamdexters.limitless.assistant.vision.CameraFrameManager.getFrame()
                if (latestFrame != null) {
                    val base64Image = bitmapToBase64(latestFrame)
                    try {
                        val result = geminiClient.queryGemini(query, base64Image)
                        result.getOrElse { e ->
                            Log.e("LIMITLESS_TRACE", "Gemini query failed: ${e.localizedMessage}", e)
                            getOfflineResponse(query)
                        }
                    } catch (e: Exception) {
                        Log.e("LIMITLESS_TRACE", "Gemini query failed: ${e.localizedMessage}", e)
                        getOfflineResponse(query)
                    }
                } else {
                    getOfflineResponse(query)
                }
            } else {
                getOfflineResponse(query)
            }

            withContext(Dispatchers.Main) {
                onResponseReady(responseText)
                speakResponse(tts, responseText)
            }
        }
    }

    private fun getOfflineResponse(userQuery: String): String {
        val lowerQuery = userQuery.lowercase(Locale.ROOT)
        return when {
            Regex("\\b(hi|hello|hey|greetings)\\b").containsMatchIn(lowerQuery) ->
                "Hello! I am Rhasspy, your accessibility assistant. How can I help you today?"
            lowerQuery.contains("time") || lowerQuery.contains("date") -> {
                val formatter = java.text.SimpleDateFormat("h:mm a, EEEE, MMMM d", Locale.getDefault())
                "It is currently " + formatter.format(java.util.Date())
            }
            lowerQuery.contains("screen") || lowerQuery.contains("where am i") ->
                "You are currently using the Limitless application."
            lowerQuery.contains("what can you do") || lowerQuery.contains("help") ->
                "I can describe your surroundings, read text, trigger emergency SOS, and find accessible routes."
            lowerQuery.contains("navigate") || lowerQuery.contains("route") ->
                "Routing you to your destination. Please scan a QR waypoint if prompted."
            else ->
                "I am currently offline. I can help you with camera vision, reading labels, navigation, or emergency SOS."
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
