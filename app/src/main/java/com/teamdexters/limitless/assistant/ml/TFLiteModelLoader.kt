package com.teamdexters.limitless.assistant.ml

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Utility for loading TensorFlow Lite models from the app assets directory.
 */
object TFLiteModelLoader {
    private const val TAG = "TFLiteModelLoader"

    /**
     * Loads a TFLite model from the app assets folder.
     *
     * @param context Application context used to open asset files.
     * @param modelName Filename of the .tflite model inside the assets folder.
     * @return Initialized [Interpreter] or null if the model could not be loaded.
     */
    fun loadModel(context: Context, modelName: String): Interpreter? {
        return try {
            val fileDescriptor = context.assets.openFd(modelName)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            val options = Interpreter.Options()
            Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load TFLite model '$modelName': ${e.message}")
            null
        }
    }
}
