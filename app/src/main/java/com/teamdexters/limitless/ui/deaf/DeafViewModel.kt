package com.teamdexters.limitless.ui.deaf

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.teamdexters.limitless.config.SecureKeyProvider
import com.teamdexters.limitless.core.audio.VoiceManager
import com.teamdexters.limitless.core.audio.VoiceState
import com.teamdexters.limitless.core.safety.LimitlessSmsManager
import com.teamdexters.limitless.haptics.NameHapticManager
import com.teamdexters.limitless.hazel.HazelActionDispatcher
import com.teamdexters.limitless.core.hazel.HazelCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeafViewModel @Inject constructor(
    private val voiceManager: VoiceManager,
    private val secureKeyProvider: SecureKeyProvider,
    private val hazelActionDispatcher: HazelActionDispatcher,
    private val nameHapticManager: NameHapticManager,
    private val limitlessSmsManager: LimitlessSmsManager
) : ViewModel() {

    private val _detectedAlerts = MutableStateFlow<List<String>>(emptyList())
    val detectedAlerts: StateFlow<List<String>> = _detectedAlerts.asStateFlow()

    private val _decibelLevel = MutableStateFlow(0f)
    val decibelLevel: StateFlow<Float> = _decibelLevel.asStateFlow()

    private val _sosCountdown = MutableStateFlow<Int?>(null)
    val sosCountdown: StateFlow<Int?> = _sosCountdown.asStateFlow()

    private val userName: String by lazy {
        secureKeyProvider.getUserName() ?: "User"
    }

    // Expose VoiceManager's live captions
    val liveCaptions = voiceManager.liveCaptions

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

        // Monitor VoiceManager results for name detection
        viewModelScope.launch {
            voiceManager.results.collect { result ->
                when (result) {
                    is com.teamdexters.limitless.core.audio.VoiceResult.Partial -> {
                        checkForNameAndVibrate(result.text)
                    }
                    is com.teamdexters.limitless.core.audio.VoiceResult.Final -> {
                        checkForNameAndVibrate(result.text)
                    }
                    else -> {}
                }
            }
        }
    }

    fun startListening() {
        Log.d("DEAF_MODE", "Requesting DEAF_CAPTION state")
        voiceManager.requestState(VoiceState.DEAF_CAPTION)
    }

    fun stopListening() {
        Log.d("DEAF_MODE", "Requesting IDLE state (stop captions)")
        voiceManager.requestState(VoiceState.IDLE)
    }

    private fun checkForNameAndVibrate(text: String) {
        if (text.contains(userName, ignoreCase = true)) {
            Log.d("DEAF_MODE", "Name Detected: [$userName] -> Triggering NameHapticManager")
            nameHapticManager.playNameRhythm(userName)
            addAlert("Someone said your name: $userName")
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

    fun triggerSOS() {
        if (_sosCountdown.value != null) return
        _sosCountdown.value = 5
        nameHapticManager.playSOSLoopPattern()
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
                nameHapticManager.stop()
            }
        }
    }

    fun cancelSOS() {
        _sosCountdown.value = null
        nameHapticManager.stop()
        addAlert("SOS Cancelled")
    }

    fun dismissSOS() {
        nameHapticManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
