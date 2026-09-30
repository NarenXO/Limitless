package com.teamdexters.limitless.feature.deaf.translation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for managing offline translation state and operations.
 */
class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    
    private val offlineTranslator = try {
        OfflineTranslator(application.applicationContext)
    } catch (e: Exception) {
        null
    }
    
    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState: StateFlow<TranslationUiState> = _uiState.asStateFlow()
    
    private var translationJob: Job? = null
    
    init {
        // Initialize with default languages
        initializeTranslator()
    }
    
    /**
     * Initialize the translator with current language settings.
     */
    private fun initializeTranslator() {
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            val success = offlineTranslator?.initialize(
                sourceLanguage = _uiState.value.sourceLanguage,
                targetLanguage = _uiState.value.targetLanguage
            ) ?: false
            
            if (success) {
                _uiState.value = _uiState.value.copy(
                    modelStatus = ModelStatus.READY
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    modelStatus = ModelStatus.ERROR
                )
            }
        }
    }
    
    /**
     * Set source language and reinitialize translator.
     */
    fun setSourceLanguage(language: String) {
        _uiState.value = _uiState.value.copy(
            sourceLanguage = language,
            modelStatus = ModelStatus.DOWNLOADING
        )
        initializeTranslator()
    }
    
    /**
     * Set target language and reinitialize translator.
     */
    fun setTargetLanguage(language: String) {
        _uiState.value = _uiState.value.copy(
            targetLanguage = language,
            modelStatus = ModelStatus.DOWNLOADING
        )
        initializeTranslator()
    }
    
    /**
     * Translate text and update UI state.
     */
    fun translateText(text: String) {
        if (text.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                originalText = "",
                translatedText = ""
            )
            return
        }
        
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                originalText = text
            )
            
            val translated = try {
                if (offlineTranslator?.isReady?.value == true) {
                    offlineTranslator.translate(text)
                } else {
                    "Model not ready"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                "Translation error: ${e.message}"
            }
            
            _uiState.value = _uiState.value.copy(
                translatedText = translated
            )
        }
    }
    
    /**
     * Clear translation text.
     */
    fun clearTranslation() {
        _uiState.value = _uiState.value.copy(
            originalText = "",
            translatedText = ""
        )
    }
    
    /**
     * Get download status from translator.
     */
    fun getDownloadStatus(): OfflineTranslator.DownloadStatus {
        return offlineTranslator?.downloadStatus?.value ?: OfflineTranslator.DownloadStatus.Error
    }
    
    /**
     * Clean up resources when ViewModel is cleared.
     */
    override fun onCleared() {
        super.onCleared()
        translationJob?.cancel()
        offlineTranslator?.close()
    }
}

/**
 * UI state for translation system.
 */
data class TranslationUiState(
    val sourceLanguage: String = "English",
    val targetLanguage: String = "Tamil",
    val originalText: String = "",
    val translatedText: String = "",
    val modelStatus: ModelStatus = ModelStatus.DOWNLOADING
)

/**
 * Model download status.
 */
enum class ModelStatus {
    READY,
    DOWNLOADING,
    ERROR
}
