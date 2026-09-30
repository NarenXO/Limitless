package com.teamdexters.limitless.feature.deaf.camera

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import java.util.Locale

/**
 * Label OCR Scanner using Google ML Kit Text Recognition.
 * 
 * SAFETY LIMIT: Reads printed text on labels only. Does NOT visually identify pills or medication.
 * This tool extracts text from product packaging, document labels, or medicine bottle text.
 * It is NOT a visual pill/medicine identification system.
 */
class LabelOcrScanner(private val context: Context?) {
    
    private val textRecognizer = try {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    } catch (e: Exception) {
        null
    }
    
    private var textToSpeech: TextToSpeech? = null
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()
    
    init {
        initializeTTS()
    }
    
    /**
     * Initialize Text-to-Speech engine.
     */
    private fun initializeTTS() {
        try {
            if (context != null) {
                textToSpeech = TextToSpeech(context) { status ->
                    _ttsReady.value = status == TextToSpeech.SUCCESS
                }
            } else {
                _ttsReady.value = false
            }
        } catch (e: Exception) {
            _ttsReady.value = false
        }
    }
    
    /**
     * Process a camera frame to extract text.
     */
    suspend fun processImage(imageProxy: ImageProxy): String {
        if (_isProcessing.value || textRecognizer == null) {
            return _recognizedText.value
        }
        
        _isProcessing.value = true
        
        return try {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                val result = textRecognizer!!.process(inputImage).await()
                val extractedText = result.text
                
                if (extractedText.isNotEmpty()) {
                    _recognizedText.value = extractedText
                }
                
                extractedText
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        } finally {
            _isProcessing.value = false
            imageProxy.close()
        }
    }
    
    /**
     * Read recognized text aloud using TTS.
     */
    fun readTextAloud(text: String) {
        if (!_ttsReady.value || text.isEmpty()) {
            return
        }
        
        textToSpeech?.apply {
            language = Locale.US
            setSpeechRate(0.8f)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    /**
     * Stop TTS playback.
     */
    fun stopReading() {
        textToSpeech?.stop()
    }
    
    /**
     * Clear recognized text.
     */
    fun clearText() {
        _recognizedText.value = ""
    }
    
    /**
     * Release resources.
     */
    fun release() {
        textToSpeech?.shutdown()
        textToSpeech = null
        textRecognizer?.close()
    }
}

/**
 * Extension function to await ML Kit Task result in coroutine.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result, onCancellation = null)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
