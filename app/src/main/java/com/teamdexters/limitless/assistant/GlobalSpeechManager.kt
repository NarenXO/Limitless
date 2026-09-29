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

enum class SpeechState {
    IDLE_WAKEWORD,
    ACTIVE_COMMAND
}

class GlobalSpeechManager(
    private val context: Context,
    private val intentRouter: IntentRouter,
    private val onStateChange: (SpeechState) -> Unit,
    private val onPartialText: (String) -> Unit,
    private val onIntentResult: (HazelIntent) -> Unit,
    private val onPlayChime: () -> Unit
) : RecognitionListener {

    companion object {
        private const val TAG = "GlobalSpeechManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentState = SpeechState.IDLE_WAKEWORD
    private val handler = Handler(Looper.getMainLooper())
    private var isDestroyed = false

    init {
        initRecognizer()
        startListening()
    }

    private fun initRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@GlobalSpeechManager)
        }
    }

    private fun startListening() {
        if (isDestroyed) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (currentState == SpeechState.IDLE_WAKEWORD) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 100000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 100000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 100000L)
            } else {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L)
            }
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer", e)
        }
    }

    fun triggerActiveCommand() {
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        
        handler.postDelayed({
            currentState = SpeechState.ACTIVE_COMMAND
            onStateChange(currentState)
            onPlayChime()
            startListening()
            
            // Auto-revert after 8 seconds
            handler.postDelayed({
                if (currentState == SpeechState.ACTIVE_COMMAND) {
                    revertToWakeWord()
                }
            }, 8000L)
        }, 400L)
    }

    fun revertToWakeWord() {
        handler.removeCallbacksAndMessages(null)
        currentState = SpeechState.IDLE_WAKEWORD
        onStateChange(currentState)
        speechRecognizer?.cancel()
        startListening()
    }

    fun destroy() {
        isDestroyed = true
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.stopListening()
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
        handler.postDelayed({
            if (!isDestroyed) {
                if (currentState == SpeechState.ACTIVE_COMMAND) {
                    revertToWakeWord()
                } else {
                    speechRecognizer?.cancel()
                    startListening()
                }
            }
        }, 1500L)
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
        
        if (currentState == SpeechState.IDLE_WAKEWORD) {
            val lower = best.lowercase()
            if (lower.contains("hazel") || lower.contains("hey hazel") || lower.contains("hey") || lower.contains("assistant")) {
                Log.i(TAG, "Wake word detected!")
                triggerActiveCommand()
            } else if (isFinal) {
                handler.postDelayed({ startListening() }, 500L)
            }
        } else {
            if (best.isNotEmpty()) {
                onPartialText(best)
            }
            if (isFinal) {
                if (best.isNotEmpty()) {
                    val intent = intentRouter.routeIntent(best)
                    onIntentResult(intent)
                }
                revertToWakeWord()
            }
        }
    }
}
