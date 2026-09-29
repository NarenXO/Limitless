package com.teamdexters.limitless.feature.deaf.translation

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.translate.TranslateLanguage
import com.google.mlkit.translate.Translation
import com.google.mlkit.translate.Translator
import com.google.mlkit.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.resume
import kotlinx.coroutines.resumeWithException
import com.google.android.gms.tasks.Task

/**
 * Offline translator using Google ML Kit Translation.
 * 
 * OFFLINE-SUPPORTED LANGUAGES:
 * - English (en)
 * - Spanish (es)
 * - French (fr)
 * - German (de)
 * - Hindi (hi)
 * - Tamil (ta)
 * - Telugu (te)
 * - Chinese (zh)
 * - Japanese (ja)
 * - Korean (ko)
 * - Arabic (ar)
 * - Portuguese (pt)
 * - Russian (ru)
 * - Italian (it)
 * - Dutch (nl)
 * - Turkish (tr)
 * - Polish (pl)
 * - Swedish (sv)
 * - Danish (da)
 * - Norwegian (no)
 * - Finnish (fi)
 * - Greek (el)
 * - Hebrew (iw)
 * - Thai (th)
 * - Vietnamese (vi)
 * - Indonesian (id)
 * - Malay (ms)
 * - Ukrainian (uk)
 * - Czech (cs)
 * - Romanian (ro)
 * - Hungarian (hu)
 * - Bulgarian (bg)
 * - Slovak (sk)
 * - Croatian (hr)
 * - Serbian (sr)
 * - Slovenian (sl)
 * - Lithuanian (lt)
 * - Latvian (lv)
 * - Estonian (ee)
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
            "spanish" -> "es"
            "french" -> "fr"
            "german" -> "de"
            "hindi" -> "hi"
            "tamil" -> "ta"
            "telugu" -> "te"
            "chinese" -> "zh"
            "japanese" -> "ja"
            "korean" -> "ko"
            "arabic" -> "ar"
            "portuguese" -> "pt"
            "russian" -> "ru"
            "italian" -> "it"
            "dutch" -> "nl"
            "turkish" -> "tr"
            "polish" -> "pl"
            "swedish" -> "sv"
            "danish" -> "da"
            "norwegian" -> "no"
            "finnish" -> "fi"
            "greek" -> "el"
            "hebrew" -> "iw"
            "thai" -> "th"
            "vietnamese" -> "vi"
            "indonesian" -> "id"
            "malay" -> "ms"
            "ukrainian" -> "uk"
            "czech" -> "cs"
            "romanian" -> "ro"
            "hungarian" -> "hu"
            "bulgarian" -> "bg"
            "slovak" -> "sk"
            "croatian" -> "hr"
            "serbian" -> "sr"
            "slovenian" -> "sl"
            "lithuanian" -> "lt"
            "latvian" -> "lv"
            "estonian" -> "ee"
            else -> null
        }
    }
    
    /**
     * Get list of supported languages.
     */
    fun getSupportedLanguages(): List<String> {
        return listOf(
            "English",
            "Spanish",
            "French",
            "German",
            "Hindi",
            "Tamil",
            "Telugu",
            "Chinese",
            "Japanese",
            "Korean",
            "Arabic",
            "Portuguese",
            "Russian",
            "Italian",
            "Dutch",
            "Turkish",
            "Polish",
            "Swedish",
            "Danish",
            "Norwegian",
            "Finnish",
            "Greek",
            "Hebrew",
            "Thai",
            "Vietnamese",
            "Indonesian",
            "Malay",
            "Ukrainian",
            "Czech",
            "Romanian",
            "Hungarian",
            "Bulgarian",
            "Slovak",
            "Croatian",
            "Serbian",
            "Slovenian",
            "Lithuanian",
            "Latvian",
            "Estonian"
        )
    }
}

/**
 * Extension function to await Task result in coroutine.
 */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        continuation.resume(result)
    }
    addOnFailureListener { exception ->
        continuation.resumeWithException(exception)
    }
}


