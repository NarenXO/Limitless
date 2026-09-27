package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages Text-to-Speech functionality for blind/low-vision assistance.
 * Handles speaking text with proper language settings and error handling.
 */
class TTSManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /**
     * Initialize the TextToSpeech engine.
     * Must be called before speaking any text.
     */
    fun initialize(onInitComplete: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            isInitialized = status == TextToSpeech.SUCCESS
            if (isInitialized) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
            }
            onInitComplete(isInitialized)
        }
    }

    /**
     * Speak the given text aloud.
     * @param text The text to speak
     * @param onComplete Optional callback when speaking finishes
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized) {
            onComplete?.invoke()
            return
        }

        tts?.let { ttsEngine ->
            // Set utterance progress listener for completion callback
            if (onComplete != null) {
                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        onComplete()
                    }
                    override fun onError(utteranceId: String?) {
                        onComplete()
                    }
                })
            }

            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
        }
    }

    /**
     * Stop any ongoing speech.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Release resources when no longer needed.
     */
    fun release() {
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        isInitialized = false
    }

    /**
     * Check if TTS is ready to use.
     */
    fun isReady(): Boolean = isInitialized
}
