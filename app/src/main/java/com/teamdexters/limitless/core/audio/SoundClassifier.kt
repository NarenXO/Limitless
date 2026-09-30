package com.teamdexters.limitless.core.audio

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.Timer
import kotlin.concurrent.timerTask

class SoundClassifier(private val context: Context) {
    private var classifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var timer: Timer? = null

    var listener: ((SoundCategory, String, Float) -> Unit)? = null

    fun initialize() {
        try {
            classifier = AudioClassifier.createFromFile(context, "yamnet.tflite")
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "Error initializing SoundClassifier: ${e.message}")
        }
    }

    fun startListening() {
        if (classifier == null) return
        if (audioRecord != null) return
        
        try {
            val tensor = classifier!!.createInputTensorAudio()
            audioRecord = classifier!!.createAudioRecord()
            audioRecord?.startRecording()

            timer = Timer()
            timer?.scheduleAtFixedRate(timerTask {
                audioRecord?.let { record ->
                    tensor.load(record)
                    val output = classifier!!.classify(tensor)
                    
                    val categories = output.firstOrNull()?.categories
                    categories?.maxByOrNull { it.score }?.let { topClass ->
                        if (topClass.score >= 0.3f) {
                            val category = mapCategory(topClass.label)
                            if (category != null) {
                                Log.d("LIMITLESS_TRACE", "Sound: [${topClass.label}] -> Confidence: [${topClass.score}] -> Haptic: [$category]")
                                listener?.invoke(category, topClass.label, topClass.score)
                            }
                        }
                    }
                }
            }, 500, 500)
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "Error starting SoundClassifier", e)
        }
    }

    fun stopListening() {
        timer?.cancel()
        timer = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "Error stopping SoundClassifier", e)
        }
        audioRecord = null
    }

    private fun mapCategory(label: String): SoundCategory? {
        val lowerLabel = label.lowercase()
        return when {
            lowerLabel.contains("siren") || lowerLabel.contains("alarm") || lowerLabel.contains("fire truck") -> SoundCategory.EMERGENCY
            lowerLabel.contains("knock") || lowerLabel.contains("doorbell") || lowerLabel.contains("telephone") -> SoundCategory.HOME
            lowerLabel.contains("applause") || lowerLabel.contains("laughter") || lowerLabel.contains("crying") -> SoundCategory.HUMAN
            lowerLabel.contains("speech") -> SoundCategory.SPEECH
            else -> null
        }
    }
}

enum class SoundCategory {
    EMERGENCY, HOME, HUMAN, SPEECH
}
