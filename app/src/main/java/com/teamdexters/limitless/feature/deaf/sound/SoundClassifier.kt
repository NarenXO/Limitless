package com.teamdexters.limitless.feature.deaf.sound

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * YAMNet sound classifier using TensorFlow Lite.
 * Loads yamnet.tflite from assets and processes 16kHz mono audio frames
 * for environmental sound classification.
 */
class SoundClassifier(private val context: Context?) {
    
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false
    
    companion object {
        private const val MODEL_PATH = "yamnet.tflite"
        private const val LABELS_PATH = "yamnet_label_list.txt"
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val SAMPLE_RATE = 16000
        private const val SAMPLE_COUNT = 15600 // 0.975s @ 16kHz for quantized model
        
        // Mapping from YAMNet labels to our SoundType
        private val LABEL_MAPPING = mapOf(
            "Siren" to VibrationVocabulary.SoundType.SIREN,
            "Alarm" to VibrationVocabulary.SoundType.FIRE_ALARM,
            "Fire alarm" to VibrationVocabulary.SoundType.FIRE_ALARM,
            "Smoke alarm" to VibrationVocabulary.SoundType.FIRE_ALARM,
            "Doorbell" to VibrationVocabulary.SoundType.DOORBELL,
            "Door knock" to VibrationVocabulary.SoundType.DOORBELL,
            "Dog bark" to VibrationVocabulary.SoundType.DOG_BARKING,
            "Dog" to VibrationVocabulary.SoundType.DOG_BARKING,
            "Baby cry" to VibrationVocabulary.SoundType.BABY_CRYING,
            "Crying, sobbing" to VibrationVocabulary.SoundType.BABY_CRYING,
            "Infant cry" to VibrationVocabulary.SoundType.BABY_CRYING,
            "Car horn" to VibrationVocabulary.SoundType.CAR_HORN,
            "Vehicle horn" to VibrationVocabulary.SoundType.CAR_HORN,
            "Horn" to VibrationVocabulary.SoundType.CAR_HORN
        )
    }
    
    /**
     * Initialize the YAMNet model and labels asynchronously.
     * Must be called on Dispatchers.IO.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (context == null) return@withContext false
            
            // Check if model file exists in assets
            val modelExists = try {
                context.assets.open(MODEL_PATH).close()
                true
            } catch (e: IOException) {
                false
            }
            
            if (!modelExists) {
                return@withContext false
            }
            
            // Load TFLite model
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().setNumThreads(4)
            interpreter = Interpreter(modelBuffer, options)
            isModelLoaded = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load model file from assets.
     */
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context!!.assets.openFd(MODEL_PATH)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    /**
     * Classify audio frame and return the detected sound type.
     * Returns null if classification fails or confidence is too low.
     * @param audioData FloatArray of audio samples (16kHz, mono)
     */
    fun classifyAudio(audioData: FloatArray): VibrationVocabulary.SoundType? {
        if (!isModelLoaded || interpreter == null) {
            return null
        }
        
        return try {
            // Prepare input tensor (YAMNet expects 15600 samples for quantized model)
            val inputShape = interpreter!!.getInputTensor(0).shape()
            val expectedSamples = inputShape[1]
            
            // Ensure audio data matches expected size
            val processedAudio = if (audioData.size >= expectedSamples) {
                audioData.copyOf(expectedSamples)
            } else {
                // Pad with zeros if too short
                FloatArray(expectedSamples) { if (it < audioData.size) audioData[it] else 0f }
            }
            
            // Reshape input to [1, expectedSamples]
            val inputBuffer = ByteBuffer.allocateDirect(expectedSamples * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (sample in processedAudio) {
                inputBuffer.putFloat(sample)
            }
            inputBuffer.rewind()
            
            // Prepare output tensor (521 classes for YAMNet)
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val numClasses = outputShape[1]
            val outputBuffer = Array(1) { FloatArray(numClasses) }
            
            // Run inference
            interpreter!!.run(inputBuffer, outputBuffer)
            
            // Process results
            val scores = outputBuffer[0]
            for (i in scores.indices) {
                val score = scores[i]
                if (score >= CONFIDENCE_THRESHOLD) {
                    // Map index to label (simplified - in production, load labels from file)
                    val label = getLabelForIndex(i)
                    if (label != null) {
                        for ((key, soundType) in LABEL_MAPPING) {
                            if (label.contains(key, ignoreCase = true)) {
                                return soundType
                            }
                        }
                    }
                }
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get label for class index (simplified - in production, load from yamnet_label_list.txt)
     */
    private fun getLabelForIndex(index: Int): String? {
        // Simplified mapping of common AudioSet indices
        val commonLabels = mapOf(
            0 to "Speech",
            1 to "Child speech",
            2 to "Conversation",
            3 to "Narration",
            4 to "Babbling",
            5 to "Speech synthesizer",
            6 to "Shout",
            7 to "Bellow",
            8 to "Yell",
            9 to "Children shouting",
            10 to "Screaming",
            11 to "Whispering",
            12 to "Laughter",
            13 to "Baby laughter",
            14 to "Giggle",
            15 to "Chuckling",
            16 to "Giggling",
            17 to "Laughing",
            18 to "Snicker",
            19 to "Crying",
            20 to "Baby cry",
            21 to "Infant cry",
            22 to "Child cry",
            23 to "Whimper",
            24 to "Whine",
            25 to "Sobbing",
            26 to "Weeping",
            27 to "Wailing",
            28 to "Siren",
            29 to "Civil defense siren",
            30 to "Air raid siren",
            31 to "Alarm",
            32 to "Fire alarm",
            33 to "Smoke alarm",
            34 to "Burglar alarm",
            35 to "Car alarm",
            36 to "Alarm clock",
            37 to "Siren, police car",
            38 to "Siren, fire truck",
            39 to "Siren, ambulance",
            40 to "Engine",
            41 to "Idling",
            42 to "Revving",
            43 to "Thrust reversing",
            44 to "Engine starting",
            45 to "Race car",
            46 to "Vehicle",
            47 to "Car",
            48 to "Bus",
            49 to "Truck",
            50 to "Motorcycle",
            51 to "Train",
            52 to "Subway",
            53 to "Aircraft",
            54 to "Helicopter",
            55 to "Boat",
            56 to "Car horn",
            57 to "Train horn",
            58 to "Ship horn",
            59 to "Horn",
            60 to "Traffic noise",
            61 to "Doorbell",
            62 to "Door knock",
            63 to "Knocking",
            64 to "Slam",
            65 to "Dog",
            66 to "Dog bark",
            67 to "Barking",
            68 to "Whimpering (dog)",
            69 to "Growling",
            70 to "Cat",
            71 to "Meow",
            72 to "Purr",
            73 to "Bird",
            74 to "Bird call",
            75 to "Bird chirp",
            76 to "Chirp",
            77 to "Crow",
            78 to "Pigeon",
            79 to "Cock",
            80 to "Chicken",
            81 to "Insect",
            82 to "Cricket",
            83 to "Mosquito",
            84 to "Fly",
            85 to "Bee",
            86 to "Buzz",
            87 to "Frog",
            88 to "Croak",
            89 to "Toad",
            90 to "Water",
            91 to "Rain",
            92 to "Thunder",
            93 to "Waterfall",
            94 to "Ocean",
            95 to "Waves",
            96 to "Wind",
            97 to "Fire",
            98 to "Crackling",
            99 to "Explosion"
        )
        return commonLabels[index]
    }
    
    /**
     * Check if the model is loaded successfully.
     */
    fun isReady(): Boolean = isModelLoaded
    
    /**
     * Release resources.
     */
    fun release() {
        interpreter?.close()
        interpreter = null
        isModelLoaded = false
    }
}
