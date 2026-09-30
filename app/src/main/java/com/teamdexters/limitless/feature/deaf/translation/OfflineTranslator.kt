package com.teamdexters.limitless.feature.deaf.translation

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

/**
 * Offline translator using Google ML Kit Translation.
 * 
 * SUPPORTED OFFLINE LANGUAGE CODES:
 * - English (TranslateLanguage.ENGLISH / "en")
 * - Tamil (TranslateLanguage.TAMIL / "ta")
 * - Hindi (TranslateLanguage.HINDI / "hi")
 * - Spanish (TranslateLanguage.SPANISH / "es")
 * - French (TranslateLanguage.FRENCH / "fr")
 * 
 * Models are downloaded on demand and cached locally for offline use.
 */
class OfflineTranslator(private val context: Context) {
    
    private var currentTranslator: Translator? = null
    private val modelManager = RemoteModelManager.getInstance()
    
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()
    
    enum class DownloadStatus {
        Idle,
        Downloading,
        Ready,
        Error
    }
    
    /**
     * Initialize translator with source and target languages.
     * Downloads models if not already available.
     */
    suspend fun initialize(sourceLanguage: String, targetLanguage: String): Boolean {
        try {
            _downloadStatus.value = DownloadStatus.Downloading
            
            val sourceLangCode = mapLanguageToCode(sourceLanguage)
            val targetLangCode = mapLanguageToCode(targetLanguage)
            
            if (sourceLangCode == null || targetLangCode == null) {
                _downloadStatus.value = DownloadStatus.Error
                return false
            }
            
            // Check if models are already downloaded
            val sourceModel = TranslateLanguage.fromLanguageTag(sourceLangCode)
            val targetModel = TranslateLanguage.fromLanguageTag(targetLangCode)
            
            if (sourceModel != null && targetModel != null) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceModel)
                    .setTargetLanguage(targetModel)
                    .build()
                
                currentTranslator?.close()
                currentTranslator = Translation.getClient(options)
                
                val conditions = DownloadConditions.Builder()
                    .requireWifi()
                    .build()
                
                try {
                    currentTranslator?.downloadModelIfNeeded(conditions)?.await()
                    _downloadStatus.value = DownloadStatus.Ready
                    _isReady.value = true
                    return true
                } catch (e: Exception) {
                    _downloadStatus.value = DownloadStatus.Error
                    _isReady.value = false
                    return false
                }
            }
            
            _downloadStatus.value = DownloadStatus.Error
            return false
        } catch (e: Exception) {
            _downloadStatus.value = DownloadStatus.Error
            return false
        }
    }
    
    /**
     * Translate text from source to target language.
     */
    suspend fun translate(text: String): String {
        if (currentTranslator == null || !_isReady.value) {
            return text
        }
        
        return try {
            currentTranslator?.translate(text)?.await() ?: text
        } catch (e: Exception) {
            text
        }
    }
    
    /**
     * Close the translator and release resources.
     */
    fun close() {
        currentTranslator?.close()
        currentTranslator = null
        _isReady.value = false
        _downloadStatus.value = DownloadStatus.Idle
    }
    
    /**
     * Map language name to ML Kit language code.
     */
    private fun mapLanguageToCode(language: String): String? {
        return when (language.lowercase()) {
            "english" -> "en"
            "tamil" -> "ta"
            "hindi" -> "hi"
            "spanish" -> "es"
            "french" -> "fr"
            else -> null
        }
    }
    
    /**
     * Get list of supported languages.
     */
    fun getSupportedLanguages(): List<String> {
        return listOf(
            "English",
            "Tamil",
            "Hindi",
            "Spanish",
            "French"
        )
    }
}

/**
 * Extension function to await Task result in coroutine.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result, onCancellation = null)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
    addOnCanceledListener {
        continuation.cancel()
    }
}
