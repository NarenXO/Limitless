package com.teamdexters.limitless.ui.deaf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.teamdexters.limitless.deaf.CaptionListener
import com.teamdexters.limitless.deaf.VoskCaptionEngine
import com.teamdexters.limitless.deaf.YamNetSoundClassifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class DeafHomeViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _captions = MutableStateFlow<List<String>>(emptyList())
    val captions: StateFlow<List<String>> = _captions.asStateFlow()

    private val _partialCaption = MutableStateFlow<String?>("Tap START LIVE CAPTIONS and speak near your phone. Transcribed text will scroll live here in large text.")
    val partialCaption: StateFlow<String?> = _partialCaption.asStateFlow()

    private val _statusMessage = MutableStateFlow("Captions Stopped")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    val detectedSound: StateFlow<String?>

    private val captionEngine = VoskCaptionEngine(application)
    private val yamNetClassifier = YamNetSoundClassifier(application)

    init {
        detectedSound = yamNetClassifier.detectedSound
        
        captionEngine.setListener(object : CaptionListener {
            override fun onPartialResult(text: String) {
                if (text.isNotBlank()) _partialCaption.value = text
            }

            override fun onFinalResult(text: String) {
                if (text.isNotBlank()) {
                    _captions.value = _captions.value + text
                    _partialCaption.value = ""
                }
            }

            override fun onError(error: String) {
                _isCapturing.value = false
                _statusMessage.value = "Error: $error"
            }

            override fun onReady() {
                _statusMessage.value = "Listening..."
            }
        })
    }

    fun startCaptions() {
        if (_isCapturing.value) return
        _isCapturing.value = true
        _statusMessage.value = "Listening..."
        if (_partialCaption.value == "Tap START LIVE CAPTIONS and speak near your phone. Transcribed text will scroll live here in large text.") {
            _partialCaption.value = ""
        }
        captionEngine.startListening()
        yamNetClassifier.startListening()
    }

    fun stopCaptions() {
        if (!_isCapturing.value) return
        _isCapturing.value = false
        _statusMessage.value = "Captions Stopped"
        captionEngine.stopListening()
        yamNetClassifier.stopListening()
    }

    fun clearCaptions() {
        _captions.value = emptyList()
        _partialCaption.value = if (_isCapturing.value) "" else "Tap START LIVE CAPTIONS and speak near your phone. Transcribed text will scroll live here in large text."
    }

    override fun onCleared() {
        super.onCleared()
        captionEngine.destroy()
        yamNetClassifier.stopListening()
    }
}
