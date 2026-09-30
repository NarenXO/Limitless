package com.teamdexters.limitless.feature.deaf.caption

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing live caption state and recognition.
 */
class CaptionViewModel(private val context: Context) : ViewModel() {
    
    private val audioStreamer = AudioStreamer()
    private val voskEngine = VoskCaptionEngine(context)
    
    private val _uiState = MutableStateFlow(CaptionUiState())
    val uiState: StateFlow<CaptionUiState> = _uiState.asStateFlow()
    
    private var recognitionJob: Job? = null
    private var modelLoadJob: Job? = null
    
    init {
        loadModel()
    }
    
    /**
     * Load the Vosk model asynchronously.
     */
    private fun loadModel() {
        modelLoadJob = viewModelScope.launch(Dispatchers.IO) {
            val isLoaded = voskEngine.initialize()
            
            if (!isLoaded) {
                _uiState.value = _uiState.value.copy(
                    status = CaptionEngineStatus.MODEL_MISSING
                )
            }
        }
    }
    
    /**
     * Start listening for audio and generating captions.
     */
    fun startListening() {
        if (!hasMicrophonePermission()) {
            _uiState.value = _uiState.value.copy(
                status = CaptionEngineStatus.MIC_DENIED
            )
            return
        }
        
        if (!voskEngine.isReady()) {
            _uiState.value = _uiState.value.copy(
                status = CaptionEngineStatus.MODEL_MISSING
            )
            return
        }
        
        if (recognitionJob?.isActive == true) {
            return
        }
        
        recognitionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.value = _uiState.value.copy(
                    status = CaptionEngineStatus.LISTENING
                )
                
                if (!audioStreamer.initialize()) {
                    _uiState.value = _uiState.value.copy(
                        status = CaptionEngineStatus.ERROR
                    )
                    return@launch
                }
                
                val audioFlow = audioStreamer.startStreaming()
                val captionFlow = voskEngine.processAudio(audioFlow)
                
                captionFlow.catch { e ->
                    _uiState.value = _uiState.value.copy(
                        status = CaptionEngineStatus.ERROR
                    )
                }.collect { update ->
                    withContext(Dispatchers.Main) {
                        processCaptionUpdate(update)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    status = CaptionEngineStatus.ERROR
                )
            }
        }
    }
    
    /**
     * Stop listening and release resources.
     */
    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        audioStreamer.stopStreaming()
        voskEngine.reset()
        
        _uiState.value = _uiState.value.copy(
            status = CaptionEngineStatus.IDLE,
            partialText = ""
        )
    }
    
    /**
     * Clear all caption lines.
     */
    fun clearCaptions() {
        _uiState.value = _uiState.value.copy(
            captionLines = emptyList(),
            partialText = ""
        )
    }
    
    /**
     * Process a caption update from the recognition engine.
     */
    private fun processCaptionUpdate(update: CaptionUpdate) {
        val text = update.text
        
        if (text.isEmpty()) {
            return
        }
        
        if (update.isFinal) {
            val newLine = CaptionLine(
                id = generateId(),
                text = text,
                isFinal = true
            )
            
            _uiState.value = _uiState.value.copy(
                captionLines = _uiState.value.captionLines + newLine,
                partialText = ""
            )
        } else {
            _uiState.value = _uiState.value.copy(
                partialText = text
            )
        }
    }
    
    /**
     * Generate a unique ID for caption lines.
     */
    private fun generateId(): String {
        return "caption_${System.currentTimeMillis()}_${(0..999).random()}"
    }
    
    /**
     * Check if microphone permission is granted.
     */
    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        recognitionJob?.cancel()
        modelLoadJob?.cancel()
        audioStreamer.release()
        voskEngine.release()
    }
}
