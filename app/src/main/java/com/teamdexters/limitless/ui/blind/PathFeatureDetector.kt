package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages path feature detection using TensorFlow Lite.
 * Detects ramps, stairs, curbs, zebra crossings, and traffic lights from camera frames.
 * 
 * The model file path is defined as a single constant below for easy model swapping.
 */
class PathFeatureDetector(private val context: Context) {

    // SINGLE CONSTANT: Model file path - change this to swap models
    private companion object {
        const val MODEL_PATH = "blind/path_features.tflite"
        const val INPUT_SIZE = 224 // Standard input size for many TFLite models
        const val NUM_CLASSES = 5 // ramps, stairs, curbs, zebra crossings, traffic lights
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    /**
     * Initialize the path feature detector with the TFLite model.
     * @return true if initialization succeeded, false otherwise
     */
    fun initialize(): Boolean {
        return try {
            // Try to load the model from assets using the constant path
            val modelFile = FileUtil.loadMappedFile(context, MODEL_PATH)
            
            // Check if the file is a valid TFLite model (not a placeholder)
            val isValidModel = modelFile.size > 1000 // Placeholder files are typically small
            
            if (!isValidModel) {
                // Placeholder model detected
                return false
            }
            
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            
            interpreter = Interpreter(modelFile, options)
            isInitialized = true
            true
        } catch (e: IOException) {
            // Model file not found, will use fallback
            false
        } catch (e: Exception) {
            // Other initialization errors (likely invalid model format)
            false
        }
    }

    /**
     * Detect path features in the given bitmap.
     * Pipeline: preprocess → interpreter.run → postprocess → results
     * @param bitmap The image to analyze
     * @return List of detected path features with their properties
     */
    fun detectPathFeatures(bitmap: Bitmap): List<PathFeature> {
        if (!isInitialized || interpreter == null) {
            // Return empty list if not initialized
            return emptyList()
        }

        return try {
            // Preprocess: resize and convert to ByteBuffer
            val inputBuffer = preprocessImage(bitmap)
            
            // Run inference
            val outputArray = Array(1) { FloatArray(NUM_CLASSES) }
            interpreter?.run(inputBuffer, outputArray)
            
            // Postprocess: extract detections
            postprocessResults(outputArray[0], bitmap.width, bitmap.height)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Preprocess image for TFLite model input.
     * Resizes to INPUT_SIZE x INPUT_SIZE and converts to normalized ByteBuffer.
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val inputBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        inputBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        resizedBitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        
        for (pixel in intValues) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            // Normalize to [0, 1] range
            inputBuffer.putFloat(r / 255.0f)
            inputBuffer.putFloat(g / 255.0f)
            inputBuffer.putFloat(b / 255.0f)
        }
        
        resizedBitmap.recycle()
        inputBuffer.rewind()
        return inputBuffer
    }

    /**
     * Postprocess model output to extract path features.
     * Converts class probabilities to detected features with confidence scores.
     */
    private fun postprocessResults(output: FloatArray, frameWidth: Int, frameHeight: Int): List<PathFeature> {
        val features = mutableListOf<PathFeature>()
        
        // Class indices: 0=ramp, 1=stairs, 2=curb, 3=zebra_crossing, 4=traffic_light
        val classNames = listOf("ramp", "stairs", "curb", "zebra crossing", "traffic light")
        val threshold = 0.5f // Confidence threshold
        
        for (i in output.indices) {
            if (output[i] >= threshold) {
                features.add(
                    PathFeature(
                        label = classNames[i],
                        confidence = output[i],
                        boundingBox = Rect(0, 0, frameWidth, frameHeight) // Full frame for now
                    )
                )
            }
        }
        
        return features
    }

    /**
     * Check if the detector is ready to use.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Close the interpreter and release resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}

/**
 * Data class representing a detected path feature.
 */
data class PathFeature(
    val label: String,
    val confidence: Float,
    val boundingBox: Rect
)
