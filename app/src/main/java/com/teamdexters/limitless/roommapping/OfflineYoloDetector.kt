package com.teamdexters.limitless.roommapping

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class YoloDetectionResult(
    val hasRamp: Boolean,
    val hasStairs: Boolean,
    val hasWideDoor: Boolean,
    val obstacleCount: Int
)

class OfflineYoloDetector(private val context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            val modelBuffer = loadModelFile("yolov8n_float32.tflite")
            val options = Interpreter.Options().apply {
                numThreads = 4
                try {
                    val delegate = org.tensorflow.lite.gpu.GpuDelegate()
                    addDelegate(delegate)
                    Log.d("LIMITLESS_TRACE", "YOLOv8 successfully initialized with GPU Delegate")
                } catch (t: Throwable) {
                    Log.w("LIMITLESS_TRACE", "GPU Delegate ClassLoading / initialization failed (${t.localizedMessage}). Falling back to 4-thread CPU interpreter.")
                }
            }
            
            interpreter = try {
                Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                Log.e("LIMITLESS_TRACE", "Failed to initialize TFLite Interpreter: ${e.localizedMessage}")
                null
            }
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "OfflineYoloDetector: Failed to load YOLOv8 model", e)
        }
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun detect(bitmap: Bitmap): YoloDetectionResult {
        if (interpreter == null) {
            return YoloDetectionResult(false, false, false, 0)
        }

        try {
            // YOLOv8 typical input [1, 640, 640, 3]
            val inputSize = 640
            val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(inputSize * inputSize)
            resized.getPixels(intValues, 0, resized.width, 0, 0, resized.width, resized.height)

            var pixel = 0
            for (i in 0 until inputSize) {
                for (j in 0 until inputSize) {
                    val rgb = intValues[pixel++]
                    byteBuffer.putFloat(((rgb shr 16 and 0xFF) / 255.0f))
                    byteBuffer.putFloat(((rgb shr 8 and 0xFF) / 255.0f))
                    byteBuffer.putFloat(((rgb and 0xFF) / 255.0f))
                }
            }

            // Standard YOLOv8 float32 output is [1, 84, 8400]
            val output = Array(1) { Array(84) { FloatArray(8400) } }
            
            // Run inference
            interpreter?.run(byteBuffer, output)

            // Placeholder logic for YOLOv8 bounding box parsing
            // Assuming no major features for this mock implementation due to dummy model
            val hasRamp = false
            val hasStairs = false
            val hasWideDoor = false
            val obstacleCount = 0

            return YoloDetectionResult(hasRamp, hasStairs, hasWideDoor, obstacleCount)
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "OfflineYoloDetector: Error running inference", e)
            return YoloDetectionResult(false, false, false, 0)
        }
    }
}
