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

    /** Callback invoked whenever speech is triggered. Useful for live transcripts. */
    var onSpeechInvoked: ((String) -> Unit)? = null

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

            onSpeechInvoked?.invoke(text)
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
        }
    }

    /**
     * Speak the given text aloud and wait for completion using coroutines.
     * @param text The text to speak
     */
    suspend fun speakAndWait(text: String) {
        if (!isInitialized) {
            return
        }

        return suspendCancellableCoroutine { continuation ->
            tts?.let { ttsEngine ->
                ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                })

                onSpeechInvoked?.invoke(text)
                ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
            } ?: run {
                continuation.resume(Unit)
            }
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
