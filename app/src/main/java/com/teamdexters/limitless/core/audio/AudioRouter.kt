package com.teamdexters.limitless.core.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRouter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val soundClassifier = SoundClassifier(context)
    private var isSpeechTranscribing = false

    var onSpeechDetected: (() -> Unit)? = null
    var onSoundDetected: ((SoundCategory, String, Float) -> Unit)? = null

    init {
        soundClassifier.initialize()
        soundClassifier.listener = { category, label, score ->
            if (category == SoundCategory.SPEECH && score > 0.8f) {
                if (!isSpeechTranscribing) {
                    onSpeechDetected?.invoke()
                }
            } else if (category != SoundCategory.SPEECH) {
                onSoundDetected?.invoke(category, label, score)
            }
        }
    }

    fun startSensing() {
        if (!isSpeechTranscribing) {
            soundClassifier.startListening()
        }
    }

    fun stopSensing() {
        soundClassifier.stopListening()
    }

    fun setSpeechTranscribing(active: Boolean) {
        isSpeechTranscribing = active
        if (active) {
            soundClassifier.stopListening()
        } else {
            soundClassifier.startListening()
        }
    }
}
