package com.teamdexters.limitless.assistant.cloud

import android.util.Log
import com.teamdexters.limitless.config.SecureKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqWhisperClient @Inject constructor(
    private val secureKeyProvider: SecureKeyProvider
) {
    companion object {
        private const val TAG = "GroqWhisper"
        private const val TIMEOUT_MS = 15000
    }

    suspend fun transcribeAudio(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== GROQ_WHISPER_START ===")
        Log.d(TAG, "WHISPER: ENTRY - Starting transcription")
        Log.d(TAG, "WHISPER: Audio file path: ${audioFile.absolutePath}")
        Log.d(TAG, "WHISPER: Audio file exists: ${audioFile.exists()}")
        Log.d(TAG, "WHISPER: Audio file size: ${audioFile.length()} bytes")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Log.e(TAG, "WHISPER: FAILURE - Audio file is missing or empty")
            return@withContext Result.failure(Exception("Audio file is missing or empty"))
        }

        val key = secureKeyProvider.getGeminiKey()?.trim()
        Log.d(TAG, "WHISPER: API key present: ${!key.isNullOrEmpty()}")
        Log.d(TAG, "WHISPER: API key valid: ${key?.startsWith("gsk_") == true}")
        
        if (key.isNullOrEmpty() || !key.startsWith("gsk_")) {
            Log.e(TAG, "WHISPER: FAILURE - Groq key missing or invalid")
            return@withContext Result.failure(Exception("Invalid Groq Key"))
        }

        try {
            val boundary = "----WebKitFormBoundary" + System.currentTimeMillis()
            val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
            Log.d(TAG, "WHISPER: Request URL: $url")
            Log.d(TAG, "WHISPER: Request method: POST")
            Log.d(TAG, "WHISPER: Boundary: $boundary")
            Log.d(TAG, "WHISPER: Model: whisper-large-v3")
            Log.d(TAG, "WHISPER: Content-Type: audio/wav")
            
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Authorization", "Bearer $key")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            Log.d(TAG, "WHISPER: Connection configured - Timeout=${TIMEOUT_MS}ms")

            val requestStartTime = System.currentTimeMillis()
            connection.outputStream.use { outputStream ->
                val writer = PrintWriter(outputStream, true)

                // Add model part
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                writer.append("whisper-large-v3\r\n")
                Log.d(TAG, "WHISPER: Request payload - Model: whisper-large-v3")

                // Add file part
                writer.append("--$boundary\r\n")
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"${audioFile.name}\"\r\n")
                writer.append("Content-Type: audio/wav\r\n\r\n")
                writer.flush()
                Log.d(TAG, "WHISPER: Request payload - File: ${audioFile.name}, Size: ${audioFile.length()} bytes")

                FileInputStream(audioFile).use { inputStream ->
                    val bytesCopied = inputStream.copyTo(outputStream)
                    Log.d(TAG, "WHISPER: Request payload - Audio bytes uploaded: $bytesCopied")
                }
                outputStream.flush()

                writer.append("\r\n--$boundary--\r\n")
                writer.flush()
                Log.d(TAG, "WHISPER: Request payload - Multipart body complete")
            }

            val responseCode = connection.responseCode
            val requestDuration = System.currentTimeMillis() - requestStartTime
            Log.d(TAG, "WHISPER: Response received - HTTP $responseCode, Duration=${requestDuration}ms")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "WHISPER: Response JSON - $responseJson")
                
                val text = JSONObject(responseJson).optString("text", "").trim()
                val textLength = text.length
                val wordCount = text.split("\\s+".toRegex()).size
                
                Log.d(TAG, "WHISPER: Transcription quality - Text length=$textLength chars, Word count=$wordCount")
                Log.d(TAG, "WHISPER: Transcription result - '$text'")
                
                if (text.isNotEmpty()) {
                    Log.d(TAG, "=== GROQ_WHISPER_STOP ===")
                    Log.d(TAG, "WHISPER_STOP: SUCCESS - Transcription completed")
                    Result.success(text)
                } else {
                    Log.e(TAG, "=== GROQ_WHISPER_STOP ===")
                    Log.e(TAG, "WHISPER_STOP: FAILURE - Empty transcription result")
                    Result.failure(Exception("Empty transcription result"))
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "=== GROQ_WHISPER_STOP ===")
                Log.e(TAG, "WHISPER_STOP: FAILURE - HTTP $responseCode")
                Log.e(TAG, "WHISPER_STOP: Error body - $errorBody")
                Result.failure(Exception("HTTP Error: $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== GROQ_WHISPER_STOP ===")
            Log.e(TAG, "WHISPER_STOP: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }
}
