package com.teamdexters.limitless.assistant

import android.content.Context
import android.util.Log
import com.teamdexters.limitless.assistant.ml.AudioPreprocessor
import com.teamdexters.limitless.assistant.ml.TFLiteModelLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.tensorflow.lite.Interpreter

/**
 * Interface for detecting wake words to activate Hazel assistant.
 */
interface WakeWordListener {
    
    /**
     * Starts listening for the wake word "Hey Hazel".
     * @param onWakeWordDetected Callback function to execute when wake word is detected
     */
    fun startListening(onWakeWordDetected: () -> Unit)
    
    /**
     * Stops listening for the wake word.
     * Should be called when not needed to conserve resources.
     */
    fun stopListening()
}

/**
 * Real TFLite implementation of [WakeWordListener] for "Hey Hazel" keyword detection.
 *
 * @param context Android [Context] required to load the model asset.
 * @param threshold Confidence threshold for keyword detection (default 0.7f).
 */
class DefaultWakeWordListener(
    private val context: Context? = null,
    private val threshold: Float = 0.7f
) : WakeWordListener {

    companion object {
        private const val TAG = "WakeWordListener"
        private const val MODEL_NAME = "hey_hazel.tflite"
        private const val COOLDOWN_MS = 2000L
    }

    private var isListening = false
    private var interpreter: Interpreter? = null
    private var audioPreprocessor: AudioPreprocessor? = null
    private var scope: CoroutineScope? = null
    private var lastDetectionTime = 0L

    override fun startListening(onWakeWordDetected: () -> Unit) {
        if (isListening) return
        isListening = true

        if (context == null) {
            Log.w(TAG, "Context is null. TFLite wake-word detection pipeline cannot be initialized. Operating in no-op mode.")
            return
        }

        val loadedInterpreter = TFLiteModelLoader.loadModel(context, MODEL_NAME)
        if (loadedInterpreter == null) {
            Log.w(TAG, "Model file '$MODEL_NAME' missing or invalid. WakeWordListener operating in no-op mode without crashing.")
            return
        }

        this.interpreter = loadedInterpreter

        val inputShape = try {
            loadedInterpreter.getInputTensor(0).shape()
        } catch (e: Exception) {
            intArrayOf(1, 43, 40, 1)
        }

        val outputShape = try {
            loadedInterpreter.getOutputTensor(0).shape()
        } catch (e: Exception) {
            intArrayOf(1, 2)
        }

        val numClasses = if (outputShape.size > 1) outputShape[1] else 2

        val preprocessor = AudioPreprocessor(inputShape = inputShape)
        this.audioPreprocessor = preprocessor

        val coroutineScope = CoroutineScope(Dispatchers.Default + Job())
        this.scope = coroutineScope

        preprocessor.startRecording(coroutineScope) { floatChunk ->
            if (!isListening) return@startRecording

            try {
                val outputBuffer = Array(1) { FloatArray(numClasses) }
                val reshapedInput = reshapeInput(floatChunk, inputShape)

                loadedInterpreter.run(reshapedInput, outputBuffer)

                val wakeWordConfidence = if (numClasses > 1) outputBuffer[0][1] else outputBuffer[0][0]

                if (wakeWordConfidence >= threshold) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastDetectionTime >= COOLDOWN_MS) {
                        lastDetectionTime = currentTime
                        Log.i(TAG, "Wake word 'Hey Hazel' detected! Confidence: $wakeWordConfidence")
                        onWakeWordDetected()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Inference execution error: ${e.message}", e)
            }
        }
    }

    private fun reshapeInput(flatArray: FloatArray, shape: IntArray): Any {
        return when (shape.size) {
            2 -> Array(shape[0]) { i ->
                FloatArray(shape[1]) { j ->
                    val idx = i * shape[1] + j
                    if (idx < flatArray.size) flatArray[idx] else 0f
                }
            }
            4 -> Array(shape[0]) { i ->
                Array(shape[1]) { j ->
                    Array(shape[2]) { k ->
                        FloatArray(shape[3]) { l ->
                            val idx = ((i * shape[1] + j) * shape[2] + k) * shape[3] + l
                            if (idx < flatArray.size) flatArray[idx] else 0f
                        }
                    }
                }
            }
            else -> flatArray
        }
    }

    override fun stopListening() {
        isListening = false
        audioPreprocessor?.stopRecording()
        audioPreprocessor = null
        scope?.cancel()
        scope = null
        try {
            interpreter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing interpreter: ${e.message}")
        }
        interpreter = null
    }

    fun isActive(): Boolean = isListening
}