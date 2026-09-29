package com.teamdexters.limitless.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class PcmWakeWordSpotter(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun start() {
        if (isRecording.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w("PcmWakeWordSpotter", "Missing RECORD_AUDIO permission")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("PcmWakeWordSpotter", "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            Log.d("PcmWakeWordSpotter", "Started recording...")

            recordingJob = coroutineScope.launch {
                val buffer = ShortArray(bufferSize)
                var consecutiveHighEnergyFrames = 0
                val energyThreshold = 1500.0 // Adjusted for typical speech
                val requiredFrames = 5 // Roughly 100-200ms of sustained speech energy

                while (isRecording.get() && isActive) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        var sum = 0.0
                        for (i in 0 until readResult) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / readResult)

                        if (rms > energyThreshold) {
                            consecutiveHighEnergyFrames++
                            if (consecutiveHighEnergyFrames >= requiredFrames) {
                                // "Wake word" pattern detected via energy simulation
                                Log.d("PcmWakeWordSpotter", "Wake word pattern detected! (RMS: $rms)")
                                
                                // Reset to avoid multiple rapid triggers
                                consecutiveHighEnergyFrames = 0
                                
                                // Pause recording internally
                                pause()
                                
                                // Fire callback on main thread
                                withContext(Dispatchers.Main) {
                                    onWakeWordDetected()
                                }
                            }
                        } else {
                            consecutiveHighEnergyFrames = 0
                        }
                    } else {
                        delay(10)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PcmWakeWordSpotter", "Error starting recording: ${e.message}", e)
        }
    }

    fun pause() {
        if (!isRecording.get()) return
        isRecording.set(false)
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("PcmWakeWordSpotter", "Error stopping recording: ${e.message}", e)
        } finally {
            audioRecord = null
        }
        Log.d("PcmWakeWordSpotter", "Paused recording.")
    }
}
