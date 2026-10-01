package com.teamdexters.limitless.ui.deaf

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class DeafHomeViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val TAG = "LIMITLESS_TRACE"

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _captions = MutableStateFlow("")
    val captions: StateFlow<String> = _captions.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _statusMessage = MutableStateFlow("Stopped")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(24)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    
    fun toggleFontSize() {
        _fontSizeSp.value = if (_fontSizeSp.value == 24) 28 else 24
    }

    fun startCaptions() {
        if (_isCapturing.value) return
        
        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication())
                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _statusMessage.value = "Listening..."
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    
                    override fun onError(error: Int) {
                        if (!_isCapturing.value) return
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            startSpeechRecognizer()
                        } else {
                            _statusMessage.value = "Error $error"
                            startSpeechRecognizer()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _captions.value = _captions.value + " " + matches[0]
                            _partial.value = ""
                        }
                        if (_isCapturing.value) {
                            startSpeechRecognizer()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _partial.value = matches[0]
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            
            _isCapturing.value = true
            startSpeechRecognizer()
            Log.d(TAG, "Deaf: STT started engine=SpeechRecognizer")
        } catch (e: Exception) {
            Log.e(TAG, "Deaf: STT failed to start", e)
            _statusMessage.value = "Failed to start"
            _isCapturing.value = false
        }
    }

    private fun startSpeechRecognizer() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Deaf: STT restart failed", e)
        }
    }

    fun stopCaptions() {
        _isCapturing.value = false
        _statusMessage.value = "Stopped"
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {}
    }

    fun clearCaptions() {
        _captions.value = ""
        _partial.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        stopCaptions()
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {}
    }
}
