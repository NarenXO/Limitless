package com.teamdexters.limitless.assistant.ml

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Preprocesses microphone audio streams for keyword spotting inference.
 * Captures 16kHz mono 16-bit PCM audio using AudioRecord, normalizes PCM values
 * to floats [-1.0f, 1.0f], and constructs feature chunks matching the required model input shape.
 *
 * @param sampleRate Audio sampling rate in Hz (default 16000 Hz).
 * @param inputShape Configurable input shape expected by the model (default [1, 43, 40, 1]).
 */
class AudioPreprocessor(
    val sampleRate: Int = 16000,
    val inputShape: IntArray = intArrayOf(1, 43, 40, 1)
) {
    companion object {
        private const val TAG = "AudioPreprocessor"
    }

    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null

    /**
     * Total number of float elements required by the input shape.
     */
    val totalInputElements: Int
        get() = inputShape.reduce { acc, dim -> acc * dim }

    /**
     * Starts audio recording and preprocessing loop on a background thread.
     *
     * @param scope CoroutineScope for running background audio capture.
     * @param onAudioChunkReady Callback invoked whenever a full input chunk is ready for inference.
     */
    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunkReady: (FloatArray) -> Unit
    ) {
        if (recordingJob?.isActive == true) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = maxOf(minBufferSize, totalInputElements * 2)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize.")
                return
            }

            try {
                audioRecord?.startRecording()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "AudioRecord failed to start recording", e)
                audioRecord?.release()
                audioRecord = null
                return
            }

            recordingJob = scope.launch(Dispatchers.IO) {
                val shortBuffer = ShortArray(1024)
                val accumFloatBuffer = FloatArray(totalInputElements)
                var accumIndex = 0

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readCount > 0) {
                        for (i in 0 until readCount) {
                            // Normalize 16-bit signed PCM short (-32768..32767) to Float (-1.0f..1.0f)
                            accumFloatBuffer[accumIndex++] = shortBuffer[i] / 32768.0f

                            if (accumIndex >= totalInputElements) {
                                val chunk = accumFloatBuffer.copyOf()
                                onAudioChunkReady(chunk)

                                // Sliding window step (shift by half the element size to preserve continuity)
                                val step = totalInputElements / 2
                                if (step in 1 until totalInputElements) {
                                    System.arraycopy(accumFloatBuffer, step, accumFloatBuffer, 0, totalInputElements - step)
                                    accumIndex = totalInputElements - step
                                } else {
                                    accumIndex = 0
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in startRecording: ${e.message}", e)
        }
    }

    /**
     * Stops audio recording and releases hardware audio resources.
     */
    fun stopRecording() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Exception stopping AudioRecord: ${e.message}", e)
        } finally {
            audioRecord = null
        }
    }
}
