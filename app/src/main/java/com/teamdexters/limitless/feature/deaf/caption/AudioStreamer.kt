package com.teamdexters.limitless.feature.deaf.caption

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Wrapper around AudioRecord for capturing audio data.
 * Configured for 16kHz mono 16-bit PCM (Vosk requirement).
 */
class AudioStreamer {
    private var audioRecord: AudioRecord? = null
    private val bufferSize: Int = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    companion object {
        private const val TAG = "AudioStreamer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Start recording audio.
     * @param context Android context for permission checking.
     * @return AudioRecord instance or null if initialization fails
     */
    fun startRecording(context: Context? = null): AudioRecord? {
        // Check RECORD_AUDIO permission before creating AudioRecord
        if (context != null) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                Log.w(TAG, "RECORD_AUDIO permission not granted. Cannot start audio recording.")
                return null
            }
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize.")
                release()
                return null
            }

            try {
                audioRecord?.startRecording()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioRecord failed to start recording", e)
                release()
                return null
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException: RECORD_AUDIO permission not granted", e)
                release()
                return null
            }
            return audioRecord
        } catch (e: SecurityException) {
            // Permission denied
            Log.e(TAG, "SecurityException during AudioRecord creation", e)
            release()
            return null
        } catch (e: IllegalStateException) {
            // AudioRecord not properly initialized
            Log.e(TAG, "IllegalStateException during AudioRecord creation", e)
            release()
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Exception during AudioRecord creation", e)
            release()
            return null
        }
    }

    /**
     * Read audio data from the recorder.
     * @param buffer Byte array to fill with audio data
     * @return Number of bytes read, or 0 if error
     */
    fun readAudio(buffer: ByteArray): Int {
        return audioRecord?.read(buffer, 0, buffer.size) ?: 0
    }

    /**
     * Stop recording and release resources.
     */
    fun stopRecording() {
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping AudioRecord", e)
        } finally {
            release()
        }
    }

    /**
     * Release AudioRecord resources.
     */
    private fun release() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Exception releasing AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }
}
