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
import com.teamdexters.limitless.core.audio.AudioRouter
import com.teamdexters.limitless.core.audio.SoundCategory
import com.teamdexters.limitless.core.safety.LimitlessSmsManager
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
    private val hapticManager: HapticManager,
    private val audioRouter: AudioRouter,
    private val limitlessSmsManager: LimitlessSmsManager
) : ViewModel() {

    private val _liveCaptions = MutableStateFlow("")
    val liveCaptions: StateFlow<String> = _liveCaptions.asStateFlow()

    private val _detectedAlerts = MutableStateFlow<List<String>>(emptyList())
    val detectedAlerts: StateFlow<List<String>> = _detectedAlerts.asStateFlow()

    private val _currentSoundCategory = MutableStateFlow<SoundCategory?>(null)
    val currentSoundCategory: StateFlow<SoundCategory?> = _currentSoundCategory.asStateFlow()

    private val _decibelLevel = MutableStateFlow(0f)
    val decibelLevel: StateFlow<Float> = _decibelLevel.asStateFlow()

    private val _sosCountdown = MutableStateFlow<Int?>(null)
    val sosCountdown: StateFlow<Int?> = _sosCountdown.asStateFlow()

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
                        triggerSOS()
                    }
                    else -> {}
                }
            }
        }

        audioRouter.onSpeechDetected = {
            viewModelScope.launch(Dispatchers.Main) {
                startSpeechRecognizer()
            }
        }

        audioRouter.onSoundDetected = { category, label, score ->
            viewModelScope.launch(Dispatchers.Default) {
                when (category) {
                    SoundCategory.EMERGENCY -> { hapticManager.playDangerPattern(); addAlert("Emergency: $label") }
                    SoundCategory.HOME -> { hapticManager.playDoorPattern(); addAlert("Home: $label") }
                    SoundCategory.HUMAN -> { hapticManager.playApplausePattern(); addAlert("Human: $label") }
                    else -> {}
                }
                viewModelScope.launch(Dispatchers.Main) {
                    _currentSoundCategory.value = category
                    kotlinx.coroutines.delay(2000)
                    if (_currentSoundCategory.value == category) {
                        _currentSoundCategory.value = null
                    }
                }
            }
        }
    }

    fun startListening() {
        audioRouter.startSensing()
    }

    private fun startSpeechRecognizer() {
        if (isListening) return
        
        audioRouter.setSpeechTranscribing(true)
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        _decibelLevel.value = rmsdB
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        audioRouter.setSpeechTranscribing(false)
                        isListening = false
                    }
                    override fun onError(error: Int) {
                        Log.e("LIMITLESS_TRACE", "DeafCore -> SpeechRecognizer error: $error")
                        audioRouter.setSpeechTranscribing(false)
                        isListening = false
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { text ->
                            processRecognizedText(text)
                        }
                        audioRouter.setSpeechTranscribing(false)
                        isListening = false
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
        audioRouter.stopSensing()
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        isListening = false
        Log.d("LIMITLESS_TRACE", "DeafCore -> Listening stopped")
    }

    fun triggerSOS() {
        if (_sosCountdown.value != null) return
        _sosCountdown.value = 5
        hapticManager.playSOSLoopPattern()
        addAlert("SOS Triggered")
        viewModelScope.launch {
            for (i in 4 downTo 0) {
                kotlinx.coroutines.delay(1000)
                if (_sosCountdown.value == null) return@launch // Cancelled
                _sosCountdown.value = i
            }
            if (_sosCountdown.value == 0) {
                _sosCountdown.value = null
                limitlessSmsManager.sendEmergencySOS(userName, "911")
                hapticManager.stop()
            }
        }
    }
    
    fun cancelSOS() {
        _sosCountdown.value = null
        hapticManager.stop()
        addAlert("SOS Cancelled")
    }
    
    fun dismissSOS() {
        hapticManager.stop()
    }
    
    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
