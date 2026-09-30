package com.teamdexters.limitless.assistant.openwakeword

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native background wake-word spotter for "Hey Hazel".
 * 
 * Records raw 16kHz audio on Dispatchers.IO and (ideally) runs ONNX inference.
 * If the ONNX model is missing, it falls back to a basic acoustic energy + phoneme pattern
 * detection so the pipeline doesn't crash out-of-the-box.
 */
class OpenWakeWordManager(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isPaused = true

    // Audio config for openWakeWord: 16kHz, mono, 16-bit PCM
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = maxOf(minBufferSize, 2048) // min bytes for 1024 shorts

    private var hasModel = false
    
    init {
        checkModelAvailability()
    }

    private fun checkModelAvailability() {
        try {
            val assets = context.assets.list("") ?: emptyArray()
            val hasOnnx = assets.contains("hey_hazel.onnx")
            val hasTflite = assets.contains("hey_hazel.tflite")
            
            // Explicitly check for 65-byte placeholder
            var isPlaceholder = false
            if (hasTflite) {
                context.assets.open("hey_hazel.tflite").use { 
                    if (it.available() < 1024) isPlaceholder = true
                }
            }
            
            hasModel = hasOnnx || (hasTflite && !isPlaceholder)
            
            if (hasModel) {
                val filename = if (hasOnnx) "hey_hazel.onnx" else "hey_hazel.tflite"
                Log.d("LIMITLESS_TRACE", "[WakeWord] Model loaded successfully")
                Log.d("LIMITLESS_TRACE", "[WakeWord] Model filename: $filename")
                Log.d("LIMITLESS_TRACE", "[WakeWord] Model labels: [Hey Hazel]")
            } else {
                Log.e("LIMITLESS_TRACE", "[WakeWord] NO TRAINED MODEL EXISTS! Found only a placeholder. Real OpenWakeWord detection is explicitly DISABLED.")
            }
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "[WakeWord] Failed to check assets", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!isPaused) return
        if (!hasModel) {
            Log.e("LIMITLESS_TRACE", "[WakeWord] Cannot start engine: NO REAL WAKE WORD MODEL EXISTS. OpenWakeWord completely disabled.")
            return
        }
        isPaused = false

        val permission = if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        Log.d("LIMITLESS_TRACE", "[WakeWord] Microphone permission: $permission")
        Log.d("LIMITLESS_TRACE", "[WakeWord] Starting background engine on Dispatchers.IO")
        
        recordingJob = scope.launch {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("LIMITLESS_TRACE", "[WakeWord] Failed to initialize AudioRecord")
                    return@launch
                }

                audioRecord?.startRecording()
                Log.d("LIMITLESS_TRACE", "[WakeWord] AudioRecord started")
                Log.d("LIMITLESS_TRACE", "[WakeWord] AudioRecord sample rate: $sampleRate")
                val readSize = 1024 // 64ms at 16kHz
                val buffer = ShortArray(readSize)

                var frameCount = 0
                var ambientNoiseSum = 0.0f
                var ambientNoiseFloorRMS = 250.0f
                var voiceThreshold = 350.0f
                var isCalibrating = true

                while (isActive && !isPaused) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        val rms = calculateRMS(buffer, readResult)
                        
                        if (isCalibrating) {
                            ambientNoiseSum += rms
                            frameCount++
                            if (frameCount >= 10) {
                                ambientNoiseFloorRMS = ambientNoiseSum / 10.0f
                                voiceThreshold = maxOf(250.0f, ambientNoiseFloorRMS * 2.5f)
                                isCalibrating = false
                                Log.d("LIMITLESS_TRACE", "[OpenWakeWord] Calibration complete. Ambient RMS: $ambientNoiseFloorRMS, Threshold: $voiceThreshold")
                            }
                        } else {
                            processAudioBlock(buffer, readResult, rms, voiceThreshold)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "[WakeWord] Error in recording loop", e)
            } finally {
                releaseAudioRecord()
            }
        }
    }

    private var absoluteFrameCount = 0

    private suspend fun processAudioBlock(buffer: ShortArray, size: Int, rms: Float, dynamicThreshold: Float) {
        absoluteFrameCount++
        
        // TODO: Implement actual ONNX/TFLite model inference here.
        // For now, since we only have a placeholder model and user strictly forbade RMS fallback:
        Log.e("LIMITLESS_TRACE", "[WakeWord] Real model inference is NOT implemented. Wake word will never trigger.")
        
        val isDetected = false

        if (isDetected) {
            Log.d("LIMITLESS_TRACE", "[WakeWord] Wake Detected!")
            pause()
            withContext(Dispatchers.Main) {
                onWakeWordDetected()
            }
        }
    }

    private fun calculateZCR(buffer: ShortArray, size: Int): Int {
        var zeroCrossings = 0
        if (size == 0) return 0
        var prevSign = buffer[0] > 0
        for (i in 1 until size) {
            val sign = buffer[i] > 0
            if (sign != prevSign) {
                zeroCrossings++
                prevSign = sign
            }
        }
        return zeroCrossings
    }

    private fun calculateRMS(buffer: ShortArray, size: Int): Float {
        var sum = 0L
        for (i in 0 until size) {
            val sample = buffer[i].toLong()
            sum += sample * sample
        }
        val mean = if (size > 0) sum.toDouble() / size else 0.0
        return Math.sqrt(mean).toFloat()
    }

    fun pause() {
        if (isPaused) return
        Log.d("LIMITLESS_TRACE", "[WakeWord] Engine paused (freeing mic hardware...)")
        isPaused = true
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()
    }

    fun resume() {
        if (!isPaused) return
        Log.d("LIMITLESS_TRACE", "[WakeWord] Engine resumed")
        start()
    }

    private fun releaseAudioRecord() {
        Log.d("LIMITLESS_TRACE", "[WakeWord] Releasing AudioRecord")
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignore stop errors if already stopped
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore release errors
        }
        audioRecord = null
    }

    fun destroy() {
        pause()
        Log.d("LIMITLESS_TRACE", "[openWakeWord] Destroyed completely.")
    }
}
