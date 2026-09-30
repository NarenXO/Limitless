package com.teamdexters.limitless.feature.deaf.caption

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
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
class CaptionViewModel : ViewModel() {
    
    private var context: Context? = null
    private var audioStreamer: AudioStreamer? = null
    private var voskEngine: VoskCaptionEngine? = null
    
    private val _uiState = MutableStateFlow(CaptionUiState())
    val uiState: StateFlow<CaptionUiState> = _uiState.asStateFlow()
    
    private var recognitionJob: Job? = null
    private var modelLoadJob: Job? = null
    private var useFallback = false
    private var enginesInitialized = false
    
    /**
     * Set the context for the ViewModel.
     * Call this from Compose with LocalContext.current.
     */
    fun setContext(ctx: Context) {
        this.context = ctx
        
        // Initialize engines only once when context is available
        if (!enginesInitialized) {
            enginesInitialized = true
            
            // Initialize AudioStreamer
            audioStreamer = try {
                AudioStreamer()
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "CaptionViewModel: Failed to create AudioStreamer", e)
                null
            }
            
            // Initialize VoskCaptionEngine with context
            voskEngine = try {
                VoskCaptionEngine(ctx)
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "CaptionViewModel: Failed to create VoskCaptionEngine", e)
                null
            }
            
            // Load model after engines are initialized
            loadModel()
        }
    }
    
    /**
     * Load the Vosk model asynchronously.
     */
    private fun loadModel() {
        modelLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val isLoaded = voskEngine?.initialize() ?: false
                
                if (isLoaded) {
                    useFallback = voskEngine?.isUsingFallback() ?: false
                    Log.d("LIMITLESS_TRACE", "CaptionViewModel: Model loaded, fallback=$useFallback")
                    _uiState.value = _uiState.value.copy(
                        status = CaptionEngineStatus.IDLE
                    )
                } else {
                    Log.w("LIMITLESS_TRACE", "CaptionViewModel: Model failed to load, using fallback")
                    _uiState.value = _uiState.value.copy(
                        status = CaptionEngineStatus.IDLE
                    )
                }
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "CaptionViewModel: Error loading model", e)
                _uiState.value = _uiState.value.copy(
                    status = CaptionEngineStatus.IDLE
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
        
        val engine = voskEngine
        if (engine == null || !engine.isReady()) {
            _uiState.value = _uiState.value.copy(
                status = CaptionEngineStatus.MODEL_MISSING
            )
            return
        }
        
        if (recognitionJob?.isActive == true) {
            return
        }
        
        _uiState.value = _uiState.value.copy(
            status = CaptionEngineStatus.LISTENING
        )
        
        if (useFallback) {
            // Use Android SpeechRecognizer fallback
            engine.startListeningWithFallback()
            
            // Collect from caption flow
            recognitionJob = viewModelScope.launch {
                engine.captionFlow?.catch { e ->
                    Log.e("LIMITLESS_TRACE", "CaptionViewModel: Caption flow error", e)
                    _uiState.value = _uiState.value.copy(
                        status = CaptionEngineStatus.ERROR
                    )
                }?.collect { update ->
                    processCaptionUpdate(update)
                }
            }
            
            Log.d("LIMITLESS_TRACE", "CaptionViewModel: Started listening with SpeechRecognizer fallback")
        } else {
            // Use Vosk with AudioStreamer
            recognitionJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val streamer = audioStreamer
                    if (streamer == null || !streamer.initialize()) {
                        _uiState.value = _uiState.value.copy(
                            status = CaptionEngineStatus.ERROR
                        )
                        return@launch
                    }
                    
                    Log.d("LIMITLESS_TRACE", "CaptionViewModel: AudioStreamer initialized")
                    
                    val audioFlow = streamer.startStreaming()
                    val captionFlow = engine.processAudio(audioFlow)
                    
                    captionFlow.catch { e ->
                        Log.e("LIMITLESS_TRACE", "CaptionViewModel: Caption flow error", e)
                        _uiState.value = _uiState.value.copy(
                            status = CaptionEngineStatus.ERROR
                        )
                    }.collect { update ->
                        withContext(Dispatchers.Main) {
                            processCaptionUpdate(update)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LIMITLESS_TRACE", "CaptionViewModel: Recognition error", e)
                    _uiState.value = _uiState.value.copy(
                        status = CaptionEngineStatus.ERROR
                    )
                }
            }
            
            Log.d("LIMITLESS_TRACE", "CaptionViewModel: Started listening with Vosk")
        }
    }
    
    /**
     * Stop listening and release resources.
     */
    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        
        if (useFallback) {
            voskEngine?.stopListeningWithFallback()
        } else {
            audioStreamer?.stopStreaming()
            voskEngine?.reset()
        }
        
        _uiState.value = _uiState.value.copy(
            status = CaptionEngineStatus.IDLE,
            partialText = ""
        )
        
        Log.d("LIMITLESS_TRACE", "CaptionViewModel: Stopped listening")
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
        val ctx = context ?: return false
        return ContextCompat.checkSelfPermission(
            ctx,
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
        audioStreamer?.release()
        voskEngine?.release()
    }
}
