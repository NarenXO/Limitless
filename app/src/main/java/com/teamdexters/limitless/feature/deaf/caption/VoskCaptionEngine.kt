package com.teamdexters.limitless.feature.deaf.caption

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.IOException

/**
 * Caption engine that performs speech-to-text recognition.
 * Supports both Vosk offline model and Android SpeechRecognizer fallback.
 */
class VoskCaptionEngine(private val context: Context?) {
    
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var isInitialized = false
    private var useFallback = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val _captionFlow = MutableSharedFlow<CaptionUpdate>()
    val captionFlow: Flow<CaptionUpdate> = _captionFlow.asSharedFlow()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    companion object {
        private const val MODEL_PATH = "vosk-model-small-en-us"
        private const val SAMPLE_RATE = 16000.0f
        private const val TAG = "LIMITLESS_TRACE"
    }
    
    /**
     * Initialize the caption engine asynchronously.
     * Tries Vosk model first, falls back to Android SpeechRecognizer.
     * Must be called on Dispatchers.IO.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (context == null) return@withContext false
            
            // Try Vosk model first
            val modelDir = File(context.filesDir, MODEL_PATH)
            
            // Copy model from assets if not present
            if (!modelDir.exists()) {
                copyModelFromAssets(modelDir)
            }
            
            // Check if model is valid
            if (modelDir.exists() && isModelValid(modelDir)) {
                // Initialize real Vosk Model and Recognizer
                try {
                    model = Model(modelDir.absolutePath)
                    recognizer = Recognizer(model, SAMPLE_RATE)
                    isInitialized = true
                    useFallback = false
                    Log.d(TAG, "VoskCaptionEngine: Vosk model loaded successfully")
                    true
                } catch (e: Throwable) {
                    Log.e(TAG, "VoskCaptionEngine: Failed to load Vosk model (native library crash), trying fallback", e)
                    // Fallback to Android SpeechRecognizer
                    useFallback = true
                    try {
                        withContext(Dispatchers.Main) {
                            initializeSpeechRecognizer()
                        }
                    } catch (innerE: Exception) {
                        Log.e(TAG, "VoskCaptionEngine: Failed to initialize fallback on main thread", innerE)
                    }
                    Log.d(TAG, "VoskCaptionEngine: Using Android SpeechRecognizer fallback after Vosk load failure")
                    true
                }
            } else {
                // Fallback to Android SpeechRecognizer - must run on main thread
                useFallback = true
                withContext(Dispatchers.Main) {
                    initializeSpeechRecognizer()
                }
                Log.d(TAG, "VoskCaptionEngine: Using Android SpeechRecognizer fallback")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "VoskCaptionEngine: Initialization failed, trying fallback", e)
            // Try fallback on any error - must run on main thread
            useFallback = true
            try {
                withContext(Dispatchers.Main) {
                    initializeSpeechRecognizer()
                }
            } catch (innerE: Exception) {
                Log.e(TAG, "VoskCaptionEngine: Failed to initialize fallback on main thread", innerE)
            }
            Log.d(TAG, "VoskCaptionEngine: Using Android SpeechRecognizer fallback after error")
            true
        }
    }
    
    /**
     * Initialize Android SpeechRecognizer as fallback.
     * Must be called on the main thread.
     */
    private fun initializeSpeechRecognizer(): Boolean {
        return try {
            if (context == null) return false
            
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                // SpeechRecognizer must be created on the main thread
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                isInitialized = true
                Log.d(TAG, "VoskCaptionEngine: SpeechRecognizer initialized successfully")
                true
            } else {
                Log.e(TAG, "VoskCaptionEngine: SpeechRecognizer not available on this device")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "VoskCaptionEngine: Failed to initialize SpeechRecognizer", e)
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
                Log.w(TAG, "VoskCaptionEngine: Model directory not found in assets, will use fallback")
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
            Log.e(TAG, "VoskCaptionEngine: Failed to copy model from assets", e)
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
        if (useFallback) {
            Log.d(TAG, "VoskCaptionEngine: Using fallback - audio streaming not supported")
            return@flow
        }
        
        val rec = recognizer ?: return@flow
        
        audioChunks.collect { chunk ->
            try {
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
            } catch (e: Throwable) {
                Log.e(TAG, "VoskCaptionEngine: Native library error during audio processing", e)
                // Continue to next chunk, don't crash the app
            }
        }
        
        // Emit any remaining final result
        try {
            val finalResult = rec.finalResult
            val text = parseVoskResult(finalResult)
            if (text.isNotEmpty()) {
                emit(CaptionUpdate(text, isFinal = true))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "VoskCaptionEngine: Native library error getting final result", e)
        }
    }
    
    /**
     * Start listening with Android SpeechRecognizer fallback.
     * This is used when Vosk model is not available.
     * Must be called on the main thread.
     */
    fun startListeningWithFallback() {
        if (!useFallback || speechRecognizer == null) {
            return
        }
        
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "VoskCaptionEngine: SpeechRecognizer ready for speech")
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "VoskCaptionEngine: Speech detected")
                }
                
                override fun onRmsChanged(rmsdB: Float) {}
                
                override fun onBufferReceived(buffer: ByteArray?) {}
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "VoskCaptionEngine: Speech ended")
                }
                
                override fun onError(error: Int) {
                    Log.e(TAG, "VoskCaptionEngine: SpeechRecognizer error: $error")
                }
                
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "VoskCaptionEngine: Final result: $text")
                        coroutineScope.launch {
                            _captionFlow.emit(CaptionUpdate(text, isFinal = true))
                        }
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        Log.d(TAG, "VoskCaptionEngine: Partial result: $text")
                        coroutineScope.launch {
                            _captionFlow.emit(CaptionUpdate(text, isFinal = false))
                        }
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "VoskCaptionEngine: Started SpeechRecognizer listening")
        } catch (e: Exception) {
            Log.e(TAG, "VoskCaptionEngine: Failed to start SpeechRecognizer", e)
        }
    }
    
    /**
     * Stop listening with Android SpeechRecognizer fallback.
     */
    fun stopListeningWithFallback() {
        if (!useFallback || speechRecognizer == null) {
            return
        }
        
        try {
            speechRecognizer?.stopListening()
            Log.d(TAG, "VoskCaptionEngine: Stopped SpeechRecognizer listening")
        } catch (e: Exception) {
            Log.e(TAG, "VoskCaptionEngine: Failed to stop SpeechRecognizer", e)
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
        try {
            recognizer?.reset()
        } catch (e: Throwable) {
            Log.e(TAG, "VoskCaptionEngine: Error resetting recognizer", e)
        }
    }
    
    /**
     * Release resources.
     */
    fun release() {
        try {
            recognizer?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "VoskCaptionEngine: Error closing recognizer", e)
        }
        recognizer = null
        
        try {
            model?.close()
        } catch (e: Throwable) {
            Log.e(TAG, "VoskCaptionEngine: Error closing model", e)
        }
        model = null
        
        isInitialized = false
    }
    
    /**
     * Check if the engine is initialized.
     */
    fun isReady(): Boolean = isInitialized
    
    /**
     * Check if using Android SpeechRecognizer fallback.
     */
    fun isUsingFallback(): Boolean = useFallback
}

/**
 * Represents a caption update from the recognition engine.
 */
data class CaptionUpdate(
    val text: String,
    val isFinal: Boolean
)
