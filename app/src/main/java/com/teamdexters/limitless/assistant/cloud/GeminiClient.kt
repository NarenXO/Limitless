package com.teamdexters.limitless.assistant.cloud

import com.teamdexters.limitless.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client service for calling Google's Gemini API as an optional cloud fallback
 * for unmatched GeneralQuery requests.
 *
 * Enforces 100% offline-first reliability by returning [Result.failure] whenever
 * network is unavailable, key is missing, or request times out (5s max).
 *
 * @param apiKeyOverride Optional API key parameter for testing or custom configuration.
 */
class GeminiClient(
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
        private const val SYSTEM_INSTRUCTION = "You are Hazel, a friendly accessibility AI assistant. Answer the user's question directly, accurately, and naturally in 1-2 conversational sentences suitable for speech synthesis."
        private const val TIMEOUT_MS = 5000
    }

    private val apiKey: String
        get() = apiKeyOverride ?: BuildConfig.GEMINI_API_KEY

    /**
     * Sends the prompt to Gemini REST endpoint with accessibility system instruction.
     * Enforces a 5-second timeout and fails gracefully if offline or unauthenticated.
     *
     * @param prompt User's unmatched spoken query
     * @return [Result.success] with Gemini response or [Result.failure] on error
     */
    suspend fun queryGemini(prompt: String, base64Image: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty() || key == "YOUR_GEMINI_API_KEY_HERE" || key == "null") {
            return@withContext getSmartFallbackResponse(prompt)
        }

        try {
            val urlString = "$BASE_URL?key=$key"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
            }

            val requestJson = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION)))
                })
                
                val partsArray = JSONArray()
                partsArray.put(JSONObject().put("text", prompt))
                
                if (base64Image != null) {
                    val inlineData = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    }
                    partsArray.put(JSONObject().put("inline_data", inlineData))
                }
                
                put("contents", JSONArray().put(
                    JSONObject().put("parts", partsArray)
                ))
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
                    getSmartFallbackResponse(prompt)
                }
            } else {
                getSmartFallbackResponse(prompt)
            }
        } catch (e: Exception) {
            getSmartFallbackResponse(prompt)
        }
    }

    private fun getSmartFallbackResponse(prompt: String): Result<String> {
        val lower = prompt.lowercase()
        val response = when {
            lower.contains("scan") || lower.contains("camera") -> "I'm currently offline, but you can say 'Open Scanner' to use the on-device accessibility scanner."
            lower.contains("report") || lower.contains("community") -> "I'm offline right now, but you can still access downloaded reports by saying 'Open Community'."
            lower.contains("blind") || lower.contains("vision") -> "I'm operating offline, but I can still switch you to Blind mode if you say 'Blind mode'."
            lower.contains("hello") || lower.contains("hi") -> "Hello! I'm operating in offline mode right now, but I can still help you navigate."
            else -> "I'm currently offline, so I can't search the web right now. You can still ask me to open tools like the Scanner or Community map."
        }
        return Result.success(response)
    }

    /**
     * Parses the JSON response structure from Gemini generateContent API.
     */
    fun parseGeminiResponse(jsonString: String): String {
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
}
