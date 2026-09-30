package com.teamdexters.limitless.ui.deaf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamdexters.limitless.config.SecureKeyProvider
import com.teamdexters.limitless.core.hazel.HazelCommand
import com.teamdexters.limitless.haptics.HapticManager
import com.teamdexters.limitless.hazel.HazelActionDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeafViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureKeyProvider: SecureKeyProvider,
    private val hazelActionDispatcher: HazelActionDispatcher,
    private val hapticManager: HapticManager
) : ViewModel() {

    private val _liveCaptions = MutableStateFlow("")
    val liveCaptions: StateFlow<String> = _liveCaptions.asStateFlow()

    private val _detectedAlerts = MutableStateFlow<List<String>>(emptyList())
    val detectedAlerts: StateFlow<List<String>> = _detectedAlerts.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val userName: String by lazy {
        secureKeyProvider.getUserName() ?: "User"
    }

    init {
        viewModelScope.launch {
            hazelActionDispatcher.systemCommand.collect { command ->
                when (command) {
                    is HazelCommand.StartSOS -> {
                        // Will be implemented later for SOS
                        addAlert("SOS Triggered via voice")
                    }
                    else -> {}
                }
            }
        }
    }

    fun startListening() {
        if (isListening) return
        
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        Log.e("LIMITLESS_TRACE", "DeafCore -> SpeechRecognizer error: $error")
                        isListening = false
                        startListening()
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            processRecognizedText(text)
                        }
                        isListening = false
                        startListening()
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            processRecognizedText(text)
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Need to specify package for continuous recognition in some Android versions
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                Log.d("LIMITLESS_TRACE", "DeafCore -> SpeechRecognizer started listening")
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "DeafCore -> Error starting SpeechRecognizer", e)
            }
        } else {
            Log.e("LIMITLESS_TRACE", "DeafCore -> Speech Recognition not available")
        }
    }

    private fun processRecognizedText(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            _liveCaptions.value = text
        }

        viewModelScope.launch(Dispatchers.Default) {
            if (text.contains(userName, ignoreCase = true)) {
                Log.d("LIMITLESS_TRACE", "DeafCore -> Name Detected: [$userName] -> Triggering Identity Haptic")
                hapticManager.playNameCallPattern()
                addAlert("Someone said your name: $userName")
            }
        }
    }

    private fun addAlert(alert: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentList = _detectedAlerts.value.toMutableList()
            if (!currentList.contains(alert)) {
                currentList.add(0, alert)
                _detectedAlerts.value = currentList
            }
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        Log.d("LIMITLESS_TRACE", "DeafCore -> SpeechRecognizer stopped")
    }
    
    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
