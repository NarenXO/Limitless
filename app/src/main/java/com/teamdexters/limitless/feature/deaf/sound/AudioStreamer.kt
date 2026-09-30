package com.teamdexters.limitless.feature.deaf.sound

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Audio streamer that captures raw PCM audio from the microphone.
 * Configured for 16kHz sample rate, Mono channel, 16-bit PCM encoding.
 * Used for environmental sound detection with YAMNet.
 */
class AudioStreamer {
    
    private var audioRecord: AudioRecord? = null
    
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 4
        private const val TAG = "LIMITLESS_TRACE"
    }
    
    /**
     * Initialize the audio recorder.
     * @throws IOException if audio recorder cannot be initialized
     */
    fun initialize(): Boolean {
        return try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            Log.d(TAG, "AudioStreamer: AudioRecord initialized successfully")
            audioRecord != null
        } catch (e: Exception) {
            Log.e(TAG, "AudioStreamer: Failed to initialize AudioRecord", e)
            false
        }
    }
    
    /**
     * Start streaming audio chunks as a Flow of byte arrays.
     * @throws IllegalStateException if audio recorder is not initialized
     */
    fun startStreaming(): Flow<ByteArray> = callbackFlow {
        val recorder = audioRecord ?: throw IllegalStateException("Audio recorder not initialized")
        
        try {
            recorder.startRecording()
            Log.d(TAG, "AudioStreamer: Recording started")
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER
            
            val buffer = ByteArray(bufferSize)
            
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = recorder.read(buffer, 0, buffer.size)
                
                if (bytesRead > 0) {
                    val chunk = buffer.copyOf(bytesRead)
                    trySend(chunk)
                }
            }
        } catch (e: Exception) {
            close(e)
        } finally {
            awaitClose {
                stopStreaming()
            }
        }
    }
    
    /**
     * Stop streaming audio.
     */
    fun stopStreaming() {
        try {
            audioRecord?.stop()
            Log.d(TAG, "AudioStreamer: Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "AudioStreamer: Error stopping recording", e)
        }
    }
    
    /**
     * Release the audio recorder resources.
     */
    fun release() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore release errors
        } finally {
            audioRecord = null
        }
    }
}
