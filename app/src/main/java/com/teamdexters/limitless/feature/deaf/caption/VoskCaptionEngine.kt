package com.teamdexters.limitless.feature.deaf.caption

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Vosk caption engine that performs offline speech-to-text recognition.
 * Uses native Vosk library for real-time speech recognition.
 */
class VoskCaptionEngine(private val context: Context?) {
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = false
    
    companion object {
        private const val MODEL_PATH = "vosk-model-small-en-us"
        private const val SAMPLE_RATE = 16000.0f
    }
    
    /**
     * Initialize the Vosk model and recognizer asynchronously.
     * Must be called on Dispatchers.IO.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (context == null) return@withContext false
            
            val modelDir = File(context.filesDir, MODEL_PATH)
            
            // Copy model from assets if not present
            if (!modelDir.exists()) {
                copyModelFromAssets(modelDir)
            }
            
            // Check if model is valid
            if (modelDir.exists() && isModelValid(modelDir)) {
                // Initialize real Vosk Model and Recognizer
                model = Model(modelDir.absolutePath)
                recognizer = Recognizer(model, SAMPLE_RATE)
                isInitialized = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Copy model from assets to app files directory.
     */
    private fun copyModelFromAssets(destDir: File) {
        try {
            if (context == null) throw IOException("Context is null")
            
            destDir.mkdirs()
            
            val assets = context.assets.list(MODEL_PATH)
            if (assets.isNullOrEmpty()) {
                throw IOException("Model directory not found in assets")
            }
            
            for (asset in assets) {
                val src = context.assets.open("$MODEL_PATH/$asset")
                val dest = File(destDir, asset)
                
                if (asset.endsWith(".bz2")) {
                    // Skip compressed files
                    continue
                }
                
                if (asset.contains(".")) {
                    // Copy file
                    dest.outputStream().use { output ->
                        src.copyTo(output)
                    }
                } else {
                    // Recursively copy directory
                    val subDir = File(destDir, asset)
                    subDir.mkdirs()
                    copyAssetDirectory("$MODEL_PATH/$asset", subDir)
                }
                
                src.close()
            }
        } catch (e: Exception) {
            throw IOException("Failed to copy model from assets", e)
        }
    }
    
    /**
     * Recursively copy asset directory.
     */
    private fun copyAssetDirectory(assetPath: String, destDir: File) {
        if (context == null) return
        
        val assets = context.assets.list(assetPath) ?: return
        
        for (asset in assets) {
            val src = context.assets.open("$assetPath/$asset")
            val dest = File(destDir, asset)
            
            if (asset.endsWith(".bz2")) {
                continue
            }
            
            if (asset.contains(".")) {
                dest.outputStream().use { output ->
                    src.copyTo(output)
                }
            } else {
                val subDir = File(destDir, asset)
                subDir.mkdirs()
                copyAssetDirectory("$assetPath/$asset", subDir)
            }
            
            src.close()
        }
    }
    
    /**
     * Check if the model directory is valid.
     */
    private fun isModelValid(modelDir: File): Boolean {
        val requiredFiles = listOf("am/mmmm", "graph", "trie")
        return requiredFiles.all { File(modelDir, it).exists() }
    }
    
    /**
     * Process audio chunks and emit caption updates.
     * Returns a Flow of CaptionUpdate containing partial and final results.
     * Feeds real audio chunks to Vosk's acceptWaveForm() and parses JSON output.
     */
    fun processAudio(audioChunks: Flow<ByteArray>): Flow<CaptionUpdate> = flow {
        val rec = recognizer ?: return@flow
        
        audioChunks.collect { chunk ->
            // Convert ByteArray to short array for Vosk (16-bit PCM)
            val numSamples = chunk.size / 2
            val shortBuffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val low = chunk[i * 2].toInt() and 0xFF
                val high = chunk[i * 2 + 1].toInt() shl 8
                shortBuffer[i] = (high or low).toShort()
            }
            
            // Feed audio to recognizer
            if (rec.acceptWaveForm(shortBuffer, shortBuffer.size)) {
                // Final result available
                val resultJson = rec.result
                val text = parseVoskResult(resultJson)
                if (text.isNotEmpty()) {
                    emit(CaptionUpdate(text, isFinal = true))
                }
            } else {
                // Partial result available
                val partialJson = rec.partialResult
                val text = parseVoskResult(partialJson)
                if (text.isNotEmpty()) {
                    emit(CaptionUpdate(text, isFinal = false))
                }
            }
        }
        
        // Emit any remaining final result
        val finalResult = rec.finalResult
        val text = parseVoskResult(finalResult)
        if (text.isNotEmpty()) {
            emit(CaptionUpdate(text, isFinal = true))
        }
    }
    
    /**
     * Parse Vosk JSON result to extract text.
     */
    private fun parseVoskResult(json: String): String {
        return try {
            val jsonObj = JSONObject(json)
            jsonObj.optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Reset the recognizer state.
     */
    fun reset() {
        recognizer?.reset()
    }
    
    /**
     * Release resources.
     */
    fun release() {
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        isInitialized = false
    }
    
    /**
     * Check if the engine is initialized.
     */
    fun isReady(): Boolean = isInitialized
}

/**
 * Represents a caption update from the recognition engine.
 */
data class CaptionUpdate(
    val text: String,
    val isFinal: Boolean
)
