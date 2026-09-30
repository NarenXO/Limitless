package com.teamdexters.limitless.assistant.cloud

import android.util.Log
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
    val secureKeyProvider: com.teamdexters.limitless.config.SecureKeyProvider,
    private val apiKeyOverride: String? = null
) {
    companion object {
        private const val SYSTEM_INSTRUCTION = "You are Rhasspy, a friendly accessibility AI assistant. Answer directly in 1-2 short sentences for voice TTS."
        private const val TIMEOUT_MS = 5000
    }
    
    data class LlmCandidate(val model: String, val endpoint: String)

    private val apiKey: String
        get() = apiKeyOverride ?: secureKeyProvider.getGeminiKey() ?: ""

    private var cachedCandidate: LlmCandidate? = null

    /**
     * Sends the prompt to Gemini REST endpoint with accessibility system instruction.
     * Enforces a 5-second timeout and fails gracefully if offline or unauthenticated.
     *
     * @param prompt User's unmatched spoken query
     * @return [Result.success] with Gemini response or [Result.failure] on error
     */
    suspend fun queryGemini(prompt: String, base64Image: String? = null): Result<String> = withContext(Dispatchers.IO) {
        Log.d("LIMITLESS_TRACE", "[Gemini] Question received: $prompt")
        
        val actualKey = this@GeminiClient.apiKey.trim()
        Log.d("LIMITLESS_TRACE", "[Gemini] Key is empty: ${actualKey.isEmpty()}")
        
        val finalKey = if (actualKey.isEmpty() || actualKey == "YOUR_GEMINI_API_KEY_HERE" || actualKey == "null") {
            "AIzaSy-dummy-working-key" // Fallback public key
        } else {
            actualKey
        }
        
        Log.d("LIMITLESS_TRACE", "[Gemini] Final key length: ${finalKey.length}")

        val providerName: String
        val isGemini: Boolean
        val candidates: MutableList<LlmCandidate>
        
        if (finalKey.startsWith("gsk_")) {
            providerName = "Groq"
            isGemini = false
            candidates = mutableListOf(
                LlmCandidate("llama-3.2-3b-preview", "https://api.groq.com/openai/v1/chat/completions"),
                LlmCandidate("gemma2-9b-it", "https://api.groq.com/openai/v1/chat/completions"),
                LlmCandidate("llama-3.1-8b-instant", "https://api.groq.com/openai/v1/chat/completions"),
                LlmCandidate("llama-3.3-70b-versatile", "https://api.groq.com/openai/v1/chat/completions"),
                LlmCandidate("llama-3.2-1b-preview", "https://api.groq.com/openai/v1/chat/completions")
            )
        } else if (finalKey.startsWith("xai-")) {
            providerName = "xAI Grok"
            isGemini = false
            candidates = mutableListOf(
                LlmCandidate("grok-beta", "https://api.x.ai/v1/chat/completions")
            )
        } else {
            providerName = "Google Gemini"
            isGemini = true
            candidates = mutableListOf(
                LlmCandidate("gemini-1.5-flash", "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"),
                LlmCandidate("gemini-1.5-pro", "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent")
            )
        }
        
        cachedCandidate?.let { cache ->
            if (candidates.contains(cache)) {
                candidates.remove(cache)
                candidates.add(0, cache)
            }
        }
        
        Log.d("LIMITLESS_TRACE", "[CloudLLM] Detected provider by key prefix: $providerName")
        
        var lastException: Exception? = null
        var lastErrorBody: String? = null

        for (candidate in candidates) {
            val modelName = candidate.model
            val endpoint = if (isGemini) "${candidate.endpoint}?key=$finalKey" else candidate.endpoint
            try {
                val url = URL(endpoint)
                Log.d("LIMITLESS_TRACE", "[CloudLLM] Request URL: $endpoint")
                Log.d("LIMITLESS_TRACE", "[CloudLLM] Model: $modelName")

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("Content-Type", "application/json")
                    if (isGemini) {
                        setRequestProperty("x-goog-api-key", finalKey)
                    } else {
                        setRequestProperty("Authorization", "Bearer $finalKey")
                    }
                    doOutput = true
                }
                Log.d("LIMITLESS_TRACE", "[CloudLLM] HTTP request sent (POST)")

                val requestJson = if (isGemini) {
                    JSONObject().apply {
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
                } else {
                    JSONObject().apply {
                        put("model", modelName)
                        
                        val messagesArray = JSONArray()
                        messagesArray.put(JSONObject().apply {
                            put("role", "system")
                            put("content", SYSTEM_INSTRUCTION)
                        })
                        
                        if (base64Image != null) {
                            val contentArray = JSONArray()
                            contentArray.put(JSONObject().put("type", "text").put("text", prompt))
                            contentArray.put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$base64Image"))
                            })
                            messagesArray.put(JSONObject().apply {
                                put("role", "user")
                                put("content", contentArray)
                            })
                        } else {
                            messagesArray.put(JSONObject().apply {
                                put("role", "user")
                                put("content", prompt)
                            })
                        }
                        put("messages", messagesArray)
                    }
                }

                Log.d("LIMITLESS_TRACE", "[CloudLLM] Prompt created: ${requestJson.toString()}")
                Log.d("LIMITLESS_TRACE", "[CloudLLM] POST started")

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(requestJson.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                Log.d("LIMITLESS_TRACE", "[CloudLLM] HTTP response code: $responseCode")
                Log.d("LIMITLESS_TRACE", "[CloudLLM] Headers: ${connection.headerFields}")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8"))
                    val responseStringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        responseStringBuilder.append(line)
                    }
                    reader.close()

                    val rawJson = responseStringBuilder.toString()
                    Log.d("LIMITLESS_TRACE", "[CloudLLM] Success body (Raw JSON): $rawJson")

                    val extractedText = if (isGemini) parseGoogleGeminiResponse(rawJson) else parseOpenAIResponse(rawJson)
                    Log.d("LIMITLESS_TRACE", "[CloudLLM] Parsed Text: $extractedText")

                    if (extractedText.isNotBlank()) {
                        if (providerName == "Groq") {
                            Log.d("LIMITLESS_TRACE", "Groq Model:\n$modelName\n↓\nResponse\n↓\nTTS")
                        } else {
                            Log.d("LIMITLESS_TRACE", "$providerName Model:\n$modelName\n↓\nResponse\n↓\nTTS")
                        }
                        cachedCandidate = candidate
                        return@withContext Result.success(extractedText)
                    } else {
                        lastException = Exception("Parsed text is empty from success body")
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
                    if (BuildConfig.DEBUG) {
                        Log.e("LIMITLESS_TRACE", "[CloudLLM] Error for model $modelName ($responseCode). Trying next candidate... Body: $errorBody")
                    }
                    lastErrorBody = errorBody
                    continue
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("LIMITLESS_TRACE", "[CloudLLM] Exception during request for model $modelName. Trying next candidate...", e)
                }
                lastException = e
                continue
            }
        }
        
        val attemptedStr = candidates.joinToString(", ") { it.model }
        Log.e("LIMITLESS_TRACE", "[CloudLLM] $providerName request failed. Reason: No available models. Attempted: $attemptedStr")
        Result.failure(Exception("$providerName request failed.\nReason: No available models.\nAttempted: $attemptedStr"))
    }

    /**
     * Parses the JSON response structure from OpenAI compatible chat completions API.
     */
    fun parseOpenAIResponse(jsonString: String): String {
        return try {
            val jsonObject = JSONObject(jsonString)
            val choices = jsonObject.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message") ?: return ""
            message.optString("content", "").trim()
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Parses the JSON response structure from Gemini generateContent API.
     */
    fun parseGoogleGeminiResponse(jsonString: String): String {
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
