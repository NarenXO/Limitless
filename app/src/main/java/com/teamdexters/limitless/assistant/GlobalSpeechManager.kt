package com.teamdexters.limitless.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class GlobalSpeechManager(
    private val context: Context,
    private val intentRouter: IntentRouter,
    private val onStateChange: (Boolean) -> Unit,
    private val onPartialText: (String) -> Unit,
    private val onIntentResult: (HazelIntent) -> Unit
) : RecognitionListener {

    companion object {
        private const val TAG = "GlobalSpeechManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    fun triggerActiveCommand() {
        if (isDestroyed) return
        handler.removeCallbacksAndMessages(null)
        
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@GlobalSpeechManager)
        }
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
        }
        
        try {
            onStateChange(true)
            speechRecognizer?.startListening(intent)
            
            // Auto-stop mic after 8 seconds to prevent endless listening
            handler.postDelayed({
                stopListening()
            }, 8000L)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer", e)
            stopListening()
        }
    }

    fun stopListening() {
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        onStateChange(false)
    }

    fun destroy() {
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        if (isDestroyed) return
        Log.e(TAG, "Speech error: $error")
        // No loop, no flash. The UI overlay handles tap-to-retry via `triggerActiveCommand`.
    }

    override fun onResults(results: Bundle?) {
        handleResults(results, isFinal = true)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        handleResults(partialResults, isFinal = false)
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun handleResults(results: Bundle?, isFinal: Boolean) {
        if (isDestroyed) return
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val best = matches?.firstOrNull()?.trim() ?: ""
        
        if (best.isNotEmpty()) {
            onPartialText(best)
        }
        
        if (isFinal) {
            if (best.isNotEmpty()) {
                val intent = intentRouter.routeIntent(best)
                onIntentResult(intent)
                stopListening()
            }
        }
    }
}
