package com.teamdexters.limitless.feature.deaf.caption

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

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
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    /**
     * Start recording audio.
     * @return AudioRecord instance or null if initialization fails
     */
    fun startRecording(): AudioRecord? {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                release()
                return null
            }

            audioRecord?.startRecording()
            return audioRecord
        } catch (e: SecurityException) {
            // Permission denied
            release()
            return null
        } catch (e: Exception) {
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
        audioRecord?.stop()
        release()
    }

    /**
     * Release AudioRecord resources.
     */
    private fun release() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore release errors
        } finally {
            audioRecord = null
        }
    }
}
