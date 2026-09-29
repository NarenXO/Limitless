package com.teamdexters.limitless.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Single managed background SpeechRecognizer for detecting the "Hey Hazel" wake-word.
 * Operates on Dispatchers.Main and restarts cleanly on timeouts/errors.
 */
class WakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    companion object {
        private const val TAG = "WakeWordManager"
    }

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.Main)
    
    private val wakeWords = listOf("hey hazel", "hazel", "hey hazle", "assistant")

    fun startListening() {
        if (isListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return

        isListening = true
        Log.d(TAG, "Starting wake-word listener...")
        initRecognizer()
        startRecognizerIntent()
    }

    fun stopListening() {
        if (!isListening) return
        Log.d(TAG, "Stopping wake-word listener...")
        isListening = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun initRecognizer() {
        if (recognizer != null) return
        
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                
                override fun onError(error: Int) {
                    if (!isListening) return
                    Log.d(TAG, "Recognizer error: $error")
                    restartListeningWithDelay()
                }

                override fun onResults(results: Bundle?) {
                    handleResults(results)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    handleResults(partialResults)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun handleResults(results: Bundle?) {
        if (!isListening) return
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        var detected = false
        if (matches != null) {
            for (match in matches) {
                val lower = match.lowercase()
                if (wakeWords.any { lower.contains(it) }) {
                    detected = true
                    break
                }
            }
        }
        
        if (detected) {
            Log.d(TAG, "Wake word detected!")
            stopListening()
            onWakeWordDetected()
        } else {
            restartListeningWithDelay()
        }
    }

    private fun startRecognizerIntent() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Extended timeouts to keep the mic open as much as possible in the background
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 100000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 100000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }
        
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            restartListeningWithDelay()
        }
    }

    private fun restartListeningWithDelay() {
        if (!isListening) return
        recognizer?.cancel()
        
        scope.launch {
            delay(1000L) // 1000ms backoff delay to prevent rapid looping
            if (isListening) {
                recognizer?.destroy()
                recognizer = null
                initRecognizer()
                startRecognizerIntent()
            }
        }
    }
}
