package com.teamdexters.limitless.deaf

import android.content.Context
import android.media.AudioRecord
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate

class YamNetSoundClassifier(private val context: Context) {
    private val TAG = "LIMITLESS_TRACE"
    private var classifier: AudioClassifier? = null
    private var audioRecord: AudioRecord? = null
    private var timer: Timer? = null

    private val _detectedSound = MutableStateFlow<String?>(null)
    val detectedSound: StateFlow<String?> = _detectedSound.asStateFlow()
    
    init {
        try {
            classifier = AudioClassifier.createFromFile(context, "yamnet.tflite")
            Log.d(TAG, "YamNetSoundClassifier: initialized from assets")
        } catch (e: Exception) {
            Log.e(TAG, "YamNetSoundClassifier: failed to initialize TFLite model, safe fallback active", e)
        }
    }

    fun startListening() {
        if (classifier == null) return
        try {
            val tensor = classifier?.createInputTensorAudio()
            val format = tensor?.tensorAudioFormat
            audioRecord = classifier?.createAudioRecord()
            audioRecord?.startRecording()

            timer = Timer()
            timer?.scheduleAtFixedRate(0, 500) {
                try {
                    tensor?.load(audioRecord)
                    val output = classifier?.classify(tensor)
                    output?.firstOrNull()?.categories?.maxByOrNull { it.score }?.let { topCategory ->
                        if (topCategory.score > 0.5f) {
                            val label = topCategory.label
                            val mappedLabel = when {
                                label.contains("Siren", true) -> "SIREN"
                                label.contains("Alarm", true) -> "FIRE_ALARM"
                                label.contains("Doorbell", true) -> "DOORBELL"
                                label.contains("Dog", true) || label.contains("Bark", true) -> "DOG_BARKING"
                                label.contains("Baby", true) || label.contains("Crying", true) -> "BABY_CRYING"
                                label.contains("Horn", true) -> "CAR_HORN"
                                else -> null
                            }
                            if (mappedLabel != null) {
                                _detectedSound.value = mappedLabel
                                VibrationVocabulary.play(context, mappedLabel)
                                // Reset after 3 seconds
                                java.util.Timer().schedule(object : java.util.TimerTask() {
                                    override fun run() {
                                        if (_detectedSound.value == mappedLabel) {
                                            _detectedSound.value = null
                                        }
                                    }
                                }, 3000)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "YamNetSoundClassifier: error during classification", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "YamNetSoundClassifier: failed to start listening", e)
        }
    }

    fun stopListening() {
        try {
            timer?.cancel()
            timer = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _detectedSound.value = null
        } catch (e: Exception) {
            Log.e(TAG, "YamNetSoundClassifier: error stopping", e)
        }
    }
}
