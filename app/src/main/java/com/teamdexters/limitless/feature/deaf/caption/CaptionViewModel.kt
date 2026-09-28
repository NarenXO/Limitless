package com.teamdexters.limitless.feature.deaf.caption

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * ViewModel for managing live caption state.
 * Coordinates between AudioStreamer and VoskCaptionEngine to provide
 * streaming speech-to-text captions.
 */
class CaptionViewModel : ViewModel() {
    private val _captions = MutableStateFlow<List<CaptionLine>>(emptyList())
    val captions: StateFlow<List<CaptionLine>> = _captions.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var voskEngine: VoskCaptionEngine? = null
    private var audioStreamer: AudioStreamer? = null
    private var isRecognitionActive = false

    /**
     * Initialize the caption engine and load the Vosk model.
     */
    fun initialize(context: Context) {
        try {
            if (voskEngine == null) {
                voskEngine = VoskCaptionEngine(context)
            }
        } catch (e: Exception) {
            // If engine creation fails, app continues without captions
            _modelLoaded.value = false
            _errorMessage.value = "Vosk model failed to load"
            return
        }

        viewModelScope.launch {
            try {
                val loaded = voskEngine?.loadModel() ?: false
                _modelLoaded.value = loaded
                if (!loaded) {
                    _errorMessage.value = "Vosk model failed to load"
                }
            } catch (e: Exception) {
                _modelLoaded.value = false
                _errorMessage.value = "Vosk model failed to load"
            }
        }
    }

    /**
     * Start listening for speech and displaying captions.
     * @param context Android context
     * @param micPermissionGranted Whether RECORD_AUDIO permission is granted
     */
    fun startListening(context: Context, micPermissionGranted: Boolean) {
        if (_isListening.value) {
            return
        }

        if (!micPermissionGranted) {
            _errorMessage.value = "Mic permission needed for captions"
            return
        }

        val engine = voskEngine
        if (engine == null || !_modelLoaded.value) {
            _errorMessage.value = "Caption model not loaded"
            return
        }

        try {
            if (!engine.initRecognizer()) {
                _errorMessage.value = "Failed to initialize recognizer"
                return
            }

            _isListening.value = true
            _errorMessage.value = null
            isRecognitionActive = true

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val streamer = AudioStreamer()
                    audioStreamer = streamer

                    val audioRecord = streamer.startRecording()
                    if (audioRecord == null) {
                        _errorMessage.value = "Failed to start audio recording"
                        _isListening.value = false
                        isRecognitionActive = false
                        return@launch
                    }

                    val buffer = ByteArray(4096)
                    
                    while (isRecognitionActive && _isListening.value) {
                        val bytesRead = streamer.readAudio(buffer)
                        if (bytesRead > 0) {
                            val audioData = buffer.copyOf(bytesRead)
                            val result = engine.processAudio(audioData)
                            
                            if (result.isPartial || result.isFinal) {
                                val text = extractTextFromJson(result.jsonResult)
                                if (text.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        updateCaptions(text, result.isPartial)
                                    }
                                }
                            }
                        } else {
                            // Error reading audio
                            break
                        }
                    }

                    // Get any final result
                    val finalResult = engine.getFinalResult()
                    if (finalResult.isFinal) {
                        val text = extractTextFromJson(finalResult.jsonResult)
                        if (text.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                updateCaptions(text, false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    _errorMessage.value = "Audio recognition error"
                } finally {
                    stopListeningInternal()
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start recognition"
        }
    }

    /**
     * Stop listening for speech.
     */
    fun stopListening() {
        isRecognitionActive = false
        audioStreamer?.stopRecording()
        voskEngine?.releaseRecognizer()
        _isListening.value = false
    }

    /**
     * Internal cleanup when stopping recognition.
     */
    private fun stopListeningInternal() {
        audioStreamer?.stopRecording()
        voskEngine?.releaseRecognizer()
        _isListening.value = false
    }

    /**
     * Update the captions list with new text.
     * @param text Recognized text
     * @param isPartial Whether this is a partial result
     */
    private fun updateCaptions(text: String, isPartial: Boolean) {
        val currentList = _captions.value.toMutableList()
        
        if (isPartial) {
            // Update or add partial result at the end
            if (currentList.isNotEmpty() && currentList.last().isPartial) {
                // Update existing partial
                currentList[currentList.size - 1] = CaptionLine(text, true)
            } else {
                // Add new partial line
                currentList.add(CaptionLine(text, true))
            }
        } else {
            // Final result - replace last partial if exists, else add new final
            if (currentList.isNotEmpty() && currentList.last().isPartial) {
                // Replace the partial with the final result
                currentList[currentList.size - 1] = CaptionLine(text, false)
            } else {
                // Add the new final result
                currentList.add(CaptionLine(text, false))
            }
        }
        
        _captions.value = currentList
    }

    /**
     * Extract text from Vosk JSON result.
     * @param jsonResult JSON string from Vosk
     * @return Extracted text or empty string
     */
    private fun extractTextFromJson(jsonResult: String): String {
        return try {
            val json = JSONObject(jsonResult)
            json.optString("text", "")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Clear all captions.
     */
    fun clearCaptions() {
        _captions.value = emptyList()
    }

    /**
     * Release all resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        stopListening()
        voskEngine?.release()
        voskEngine = null
        audioStreamer = null
    }
}
