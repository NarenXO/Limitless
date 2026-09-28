package com.teamdexters.limitless.feature.deaf.caption

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Wraps Vosk model loading and streaming speech recognition.
 * Loads the model from assets and provides streaming recognition interface.
 * Constructor is lightweight - heavy initialization happens in loadModel().
 */
class VoskCaptionEngine(private val context: Context) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null

    companion object {
        private const val TAG = "VoskCaptionEngine"
        private const val MODEL_PATH = "vosk-model-small-en-us"
        private const val SAMPLE_RATE = 16000
    }

    init {
        // Lightweight constructor - no heavy initialization here
        // Model loading happens in loadModel() method
    }

    /**
     * Load the Vosk model from assets.
     * Must be called on IO dispatcher.
     * @return true if model loaded successfully, false otherwise
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, MODEL_PATH)
            
            // Check if model already exists in files dir
            if (!modelDir.exists()) {
                // Try to extract from assets
                val extracted = extractModelFromAssets(modelDir)
                if (!extracted) {
                    // Model not found in assets
                    Log.e(TAG, "Model not found in assets: $MODEL_PATH")
                    return@withContext false
                }
            }

            // Verify model directory has required files
            if (!modelDir.exists() || !modelDir.isDirectory) {
                Log.e(TAG, "Model directory does not exist: ${modelDir.absolutePath}")
                return@withContext false
            }

            // Load the model
            model = try {
                Model(modelDir.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Vosk model from: ${modelDir.absolutePath}", e)
                null
            }
            val success = model != null
            if (success) {
                Log.d(TAG, "Vosk model loaded successfully")
            } else {
                Log.e(TAG, "Failed to create Vosk model from: ${modelDir.absolutePath}")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error loading Vosk model", e)
            false
        }
    }

    /**
     * Extract model files from assets to internal storage.
     * @param targetDir Target directory for extraction
     * @return true if extraction successful
     */
    private fun extractModelFromAssets(targetDir: File): Boolean {
        return try {
            // Check if model directory exists in assets
            val assets = context.assets.list(MODEL_PATH)
            if (assets == null || assets.isEmpty()) {
                // Model directory not found in assets
                Log.e(TAG, "Model directory not found in assets: $MODEL_PATH")
                return false
            }
            
            // Create target directory
            if (!targetDir.mkdirs()) {
                Log.e(TAG, "Failed to create target directory: ${targetDir.absolutePath}")
                return false
            }
            
            for (asset in assets) {
                val assetPath = "$MODEL_PATH/$asset"
                val targetFile = File(targetDir, asset)
                
                if (!copyAssetRecursively(assetPath, targetFile)) {
                    Log.e(TAG, "Failed to copy asset: $assetPath")
                    return false
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting model from assets", e)
            false
        }
    }

    /**
     * Recursively copy asset file or directory.
     * @return true if copy successful, false otherwise
     */
    private fun copyAssetRecursively(assetPath: String, targetFile: File): Boolean {
        return try {
            val contents = context.assets.list(assetPath)
            if (contents?.isNotEmpty() == true) {
                // It's a directory
                if (!targetFile.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: ${targetFile.absolutePath}")
                    return false
                }
                for (item in contents) {
                    if (!copyAssetRecursively("$assetPath/$item", File(targetFile, item))) {
                        return false
                    }
                }
                true
            } else {
                // It's a file
                targetFile.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset: $assetPath", e)
            false
        }
    }

    /**
     * Initialize the recognizer with the loaded model.
     * @return true if recognizer initialized successfully
     */
    fun initRecognizer(): Boolean {
        val modelRef = model ?: return false
        return try {
            recognizer = Recognizer(modelRef, SAMPLE_RATE.toFloat())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Vosk recognizer", e)
            false
        }
    }

    /**
     * Process audio data and return recognition result.
     * @param audioData Raw PCM audio data (16-bit, 16kHz, mono)
     * @return RecognitionResult containing text and partial flag
     */
    fun processAudio(audioData: ByteArray): RecognitionResult {
        val recognizerRef = recognizer ?: return RecognitionResult("", false, false)
        
        return try {
            if (recognizerRef.acceptWaveForm(audioData, audioData.size)) {
                // Final result
                val result = recognizerRef.result
                RecognitionResult(result, true, false)
            } else {
                // Partial result
                val partial = recognizerRef.partialResult
                RecognitionResult(partial, false, true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RecognitionResult("", false, false)
        }
    }

    /**
     * Get any final result that might be pending.
     * @return Final recognition result if available
     */
    fun getFinalResult(): RecognitionResult {
        val recognizerRef = recognizer ?: return RecognitionResult("", false, false)
        
        return try {
            val result = recognizerRef.finalResult
            RecognitionResult(result, true, false)
        } catch (e: Exception) {
            e.printStackTrace()
            RecognitionResult("", false, false)
        }
    }

    /**
     * Release the recognizer.
     */
    fun releaseRecognizer() {
        try {
            recognizer?.close()
        } catch (e: Exception) {
            // Ignore close errors
        } finally {
            recognizer = null
        }
    }

    /**
     * Release the model.
     */
    fun releaseModel() {
        try {
            model?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing Vosk model", e)
        } finally {
            model = null
        }
    }

    /**
     * Release all resources.
     */
    fun release() {
        releaseRecognizer()
        releaseModel()
    }
}

/**
 * Data class for recognition results.
 */
data class RecognitionResult(
    val jsonResult: String,
    val isFinal: Boolean,
    val isPartial: Boolean
)
