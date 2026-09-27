package com.teamdexters.limitless.feature.deaf.sound

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.FloatBuffer

/**
 * Manages YAMNet (TFLite) model inference for environmental sound classification.
 * Detects emergency sounds: Siren, Fire Alarm, and Doorbell.
 */
class SoundClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var isModelLoaded = false
    private var loadError: String? = null

    companion object {
        private const val TAG = "SoundClassifier"
        private const val MODEL_FILE = "yamnet.tflite"
        private const val LABEL_FILE = "yamnet_label_list.txt"
        private const val SAMPLE_RATE = 16000
        private const val WINDOW_SIZE = 15600 // ~0.975 seconds for YAMNet
        private const val HOP_SIZE = 512 // ~32ms overlap

        // Target sound labels in YAMNet model
        private val TARGET_SOUNDS = mapOf(
            "Siren" to SoundType.SIREN,
            "Alarm" to SoundType.FIRE_ALARM,
            "Smoke detector" to SoundType.FIRE_ALARM,
            "Fire alarm" to SoundType.FIRE_ALARM,
            "Doorbell" to SoundType.DOORBELL,
            "Knock" to SoundType.DOORBELL,
            "Door knock" to SoundType.DOORBELL
        )
    }

    /**
     * Load the YAMNet model and label list from assets.
     * @return true if model loaded successfully, false otherwise
     */
    fun loadModel(): Boolean {
        return try {
            val modelBuffer = loadModelFile()
            if (modelBuffer == null) {
                loadError = "Model file not found: $MODEL_FILE"
                Log.e(TAG, loadError!!)
                return false
            }

            val options = Interpreter.Options()
            interpreter = Interpreter(modelBuffer, options)
            
            labels = loadLabels()
            if (labels.isEmpty()) {
                loadError = "Label file not found: $LABEL_FILE"
                Log.e(TAG, loadError!!)
                return false
            }

            isModelLoaded = true
            loadError = null
            Log.d(TAG, "YAMNet model loaded successfully with ${labels.size} labels")
            true
        } catch (e: Exception) {
            loadError = "Failed to load model: ${e.message}"
            Log.e(TAG, "Error loading YAMNet model", e)
            false
        }
    }

    /**
     * Load TFLite model file from assets.
     */
    private fun loadModelFile(): MappedByteBuffer? {
        return try {
            val assetFileDescriptor = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load model file", e)
            null
        }
    }

    /**
     * Load label list from assets.
     */
    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(LABEL_FILE).bufferedReader().use { reader ->
                reader.readLines().map { it.trim() }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load label file", e)
            emptyList()
        }
    }

    /**
     * Classify audio data and detect target sounds.
     * @param audioData Float array of audio samples (16kHz, mono)
     * @return Detected sound type or null if no target sound detected
     */
    fun classify(audioData: FloatArray): SoundType? {
        if (!isModelLoaded || interpreter == null) {
            return null
        }

        return try {
            // Prepare input buffer (YAMNet expects specific input shape)
            val inputBuffer = prepareInputBuffer(audioData)
            
            // Prepare output buffer (YAMNet outputs 521 class probabilities)
            val outputBuffer = Array(1) { FloatArray(521) }
            
            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)
            
            // Find the highest probability class
            val probabilities = outputBuffer[0]
            var maxIndex = 0
            var maxProb = 0f
            
            for (i in probabilities.indices) {
                if (probabilities[i] > maxProb) {
                    maxProb = probabilities[i]
                    maxIndex = i
                }
            }
            
            // Get the label for the detected class
            if (maxIndex < labels.size) {
                val detectedLabel = labels[maxIndex]
                Log.d(TAG, "Detected: $detectedLabel (confidence: $maxProb)")
                
                // Check if it's a target sound
                TARGET_SOUNDS[detectedLabel]
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during classification", e)
            null
        }
    }

    /**
     * Prepare input buffer for YAMNet model.
     * YAMNet expects a specific window size of 15600 samples.
     */
    private fun prepareInputBuffer(audioData: FloatArray): FloatBuffer {
        val bufferSize = WINDOW_SIZE
        val buffer = FloatBuffer.allocate(bufferSize)
        
        // Copy audio data, pad with zeros if not enough samples
        val copyLength = minOf(audioData.size, bufferSize)
        for (i in 0 until copyLength) {
            buffer.put(audioData[i])
        }
        
        // Pad remaining with zeros
        while (buffer.hasRemaining()) {
            buffer.put(0f)
        }
        
        buffer.rewind()
        return buffer
    }

    /**
     * Check if model is loaded successfully.
     */
    fun isReady(): Boolean = isModelLoaded

    /**
     * Get the error message if model failed to load.
     */
    fun getLoadError(): String? = loadError

    /**
     * Release resources.
     */
    fun release() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
}

/**
 * Types of sounds we want to detect.
 */
enum class SoundType {
    SIREN,
    FIRE_ALARM,
    DOORBELL
}