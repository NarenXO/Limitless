package com.teamdexters.limitless.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

interface WakeWordListener {
    fun startListening(onWakeWordDetected: () -> Unit)
    fun stopListening()
}

/**
 * Android SpeechRecognizer implementation of [WakeWordListener] for "Hey Hazel" keyword detection.
 */
class DefaultWakeWordListener(
    private val context: Context? = null,
    private val threshold: Float = 0.7f // Not used for SpeechRecognizer
) : WakeWordListener, RecognitionListener {

    companion object {
        private const val TAG = "WakeWordListener"
    }

    private var isListening = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var onWakeWordDetectedCallback: (() -> Unit)? = null

    override fun startListening(onWakeWordDetected: () -> Unit) {
        if (isListening || context == null) return
        isListening = true
        onWakeWordDetectedCallback = onWakeWordDetected

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@DefaultWakeWordListener)
        }
        startListeningInternal()
    }

    private fun startListeningInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 100000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 100000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100000L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer", e)
        }
    }

    override fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onWakeWordDetectedCallback = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    
    override fun onError(error: Int) {
        if (isListening) {
            // Restart listener on error to keep it continuous
            speechRecognizer?.cancel()
            startListeningInternal()
        }
    }

    override fun onResults(results: Bundle?) {
        handleResults(results)
        if (isListening) {
            startListeningInternal()
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        handleResults(partialResults)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun handleResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null) {
            val combined = matches.joinToString(" ")
            if (combined.contains("hey hazel", ignoreCase = true)) {
                Log.i(TAG, "Wake word 'Hey Hazel' detected!")
                onWakeWordDetectedCallback?.invoke()
                
                // Briefly restart to avoid multiple rapid triggers
                speechRecognizer?.cancel()
                if (isListening) {
                    startListeningInternal()
                }
            }
        }
    }

    fun isActive(): Boolean = isListening
}