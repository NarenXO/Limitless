package com.teamdexters.limitless.feature.deaf.translation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranslationViewModel : ViewModel() {
    
    private var translator: OfflineTranslator? = null
    
    private val _sourceLanguage = MutableStateFlow("English")
    val sourceLanguage: StateFlow<String> = _sourceLanguage.asStateFlow()
    
    private val _targetLanguage = MutableStateFlow("Spanish")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()
    
    private val _downloadStatus = MutableStateFlow<OfflineTranslator.DownloadStatus>(OfflineTranslator.DownloadStatus.Idle)
    val downloadStatus: StateFlow<OfflineTranslator.DownloadStatus> = _downloadStatus.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText.asStateFlow()
    
    private val _supportedLanguages = MutableStateFlow<List<String>>(emptyList())
    val supportedLanguages: StateFlow<List<String>> = _supportedLanguages.asStateFlow()
    
    fun initialize(context: Context) {
        viewModelScope.launch {
            translator = OfflineTranslator(context)
            _supportedLanguages.value = translator?.getSupportedLanguages() ?: emptyList()
            loadLanguageModels()
        }
    }
    
    fun setSourceLanguage(language: String) {
        _sourceLanguage.value = language
        loadLanguageModels()
    }
    
    fun setTargetLanguage(language: String) {
        _targetLanguage.value = language
        loadLanguageModels()
    }
    
    private fun loadLanguageModels() {
        viewModelScope.launch {
            translator?.let { trans ->
                val success = trans.initialize(_sourceLanguage.value, _targetLanguage.value)
                _isReady.value = success
                _downloadStatus.value = trans.downloadStatus.value
            }
        }
    }
    
    fun translate(text: String) {
        viewModelScope.launch {
            if (_isReady.value) {
                val result = translator?.translate(text) ?: text
                _translatedText.value = result
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        translator?.close()
    }
}
