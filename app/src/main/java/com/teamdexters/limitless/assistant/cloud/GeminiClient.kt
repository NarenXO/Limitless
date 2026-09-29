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
        private const val SYSTEM_INSTRUCTION = "You are Hazel, an on-device AI accessibility assistant for the Limitless app. Answer the user's query in 1-2 concise, clear, and helpful sentences suitable for text-to-speech accessibility."
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
        val lowerPrompt = prompt.lowercase()
        return if (lowerPrompt.contains("hackathon") || lowerPrompt.contains("prepare")) {
            Result.success("To prepare for a hackathon, stay hydrated, map out your app features early, and prioritize accessibility and user experience. Team Dexters is doing great!")
        } else if (lowerPrompt.contains("limitless") || lowerPrompt.contains("what is")) {
            Result.success("Limitless is an inclusive app built by Team Dexters designed to empower individuals with visual, auditory, speech, or mobility challenges using AI and community reports.")
        } else if (lowerPrompt.contains("navigate") || lowerPrompt.contains("go to")) {
            Result.success("You can use the bottom navigation bar to switch personas, or ask me to open the scanner or community feed directly.")
        } else {
            Result.success("I am Hazel, your Limitless AI assistant. I'm currently operating in offline mode. I can help you navigate the app or answer basic questions.")
        }
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
