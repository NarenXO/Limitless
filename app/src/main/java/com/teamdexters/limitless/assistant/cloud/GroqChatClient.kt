package com.teamdexters.limitless.assistant.cloud

import android.util.Log
import com.teamdexters.limitless.config.SecureKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Groq LLM chat completion API.
 * Used for Hazel query processing after Whisper transcription.
 */
@Singleton
class GroqChatClient @Inject constructor(
    val secureKeyProvider: SecureKeyProvider
) {
    companion object {
        private const val TAG = "GroqChat"
        private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val MODEL = "openai/gpt-oss-120b"
        private const val TIMEOUT_MS = 15000
        private const val SYSTEM_PROMPT = "You are Hazel, a friendly accessibility AI assistant. Answer the user's question directly, accurately, and naturally in 1-2 conversational sentences suitable for speech synthesis. Do NOT mention you are an AI, do NOT mention offline mode, hackathons, or team names."
    }

    /**
     * Sends a query to Groq LLM for chat completion.
     */
    suspend fun chatCompletion(query: String): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== GROQ_CHAT_START ===")
        Log.d(TAG, "CHAT: Query: '$query'")

        val key = secureKeyProvider.getGroqApiKey().trim()
        Log.d(TAG, "CHAT: API key present: ${key.isNotEmpty()}")
        Log.d(TAG, "CHAT: API key valid: ${key.startsWith("gsk_")}")
        Log.d(TAG, "CHAT: API key length: ${key.length}")

        if (key.isEmpty() || !key.startsWith("gsk_")) {
            Log.e(TAG, "CHAT: FAILURE - Groq key missing or invalid")
            return@withContext Result.failure(Exception("Groq API key not configured. Please add GROQ_API_KEY to local.properties"))
        }

        try {
            val url = URL(BASE_URL)
            Log.d(TAG, "CHAT: Request URL: $BASE_URL")
            Log.d(TAG, "CHAT: Model: $MODEL")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            }

            val requestJson = JSONObject().apply {
                put("model", MODEL)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
                    })
                })
                put("temperature", 0.7)
                put("max_tokens", 150)
            }

            Log.d(TAG, "CHAT: Request JSON: ${requestJson.toString()}")

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestJson.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "CHAT: HTTP status: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                val responseStringBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    responseStringBuilder.append(line)
                }
                reader.close()

                val rawJson = responseStringBuilder.toString()
                Log.d(TAG, "CHAT: Response body: $rawJson")

                val responseText = parseChatResponse(rawJson)
                Log.d(TAG, "CHAT: Parsed response: '$responseText'")

                if (responseText.isNotBlank()) {
                    Result.success(responseText)
                } else {
                    Result.failure(Exception("Empty response from Groq"))
                }
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream, "UTF-8"))
                val errorBuilder = StringBuilder()
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    errorBuilder.append(line)
                }
                errorReader.close()
                val errorBody = errorBuilder.toString()
                Log.e(TAG, "CHAT: Error body: $errorBody")
                Result.failure(Exception("HTTP Error $responseCode: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "CHAT: Exception during request", e)
            Result.failure(e)
        }
    }

    private fun parseChatResponse(jsonString: String): String {
        return try {
            val jsonObject = JSONObject(jsonString)
            val choices = jsonObject.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message") ?: return ""
            val content = message.optString("content", "").trim()
            content
        } catch (e: Exception) {
            Log.e(TAG, "CHAT: Failed to parse response", e)
            ""
        }
    }
}
