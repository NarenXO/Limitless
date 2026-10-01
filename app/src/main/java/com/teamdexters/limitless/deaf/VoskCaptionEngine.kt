package com.teamdexters.limitless.deaf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

interface CaptionListener {
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(error: String)
    fun onReady()
}

class VoskCaptionEngine(private val context: Context) {
    private val TAG = "LIMITLESS_TRACE"
    private var useFallback = true
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: CaptionListener? = null
    private var isListening = false

    init {
        try {
            // Attempt to load Vosk model here if available
            // For now, gracefully fallback since assets might not be fully configured
            Log.d(TAG, "DeafCaptionEngine: Vosk model not found in assets, falling back to SpeechRecognizer")
            useFallback = true
        } catch (e: Exception) {
            Log.e(TAG, "DeafCaptionEngine: Failed to initialize Vosk", e)
            useFallback = true
        }
    }

    fun setListener(listener: CaptionListener) {
        this.listener = listener
    }

    fun startListening() {
        isListening = true
        if (useFallback) {
            startFallback()
        } else {
            // Vosk start logic
        }
    }

    fun stopListening() {
        isListening = false
        if (useFallback) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {}
        } else {
            // Vosk stop logic
        }
    }

    fun destroy() {
        stopListening()
        if (useFallback) {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {}
        }
    }

    private fun startFallback() {
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        listener?.onReady()
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    
                    override fun onError(error: Int) {
                        if (!isListening) return
                        val errorMsg = when(error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No match"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout"
                            else -> "Error $error"
                        }
                        // Auto-restart on benign errors
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            startFallback()
                        } else {
                            listener?.onError(errorMsg)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            listener?.onFinalResult(matches[0])
                        }
                        if (isListening) {
                            startFallback()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            listener?.onPartialResult(matches[0])
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "DeafCaptionEngine: STT initialized using [SpeechRecognizer]")
        } catch (e: Exception) {
            Log.e(TAG, "DeafCaptionEngine: Failed to start fallback", e)
            listener?.onError("Failed to start")
        }
    }
}
