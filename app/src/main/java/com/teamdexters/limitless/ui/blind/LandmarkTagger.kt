package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.teamdexters.limitless.data.local.dao.TaggedLocationDao
import com.teamdexters.limitless.data.local.entity.TaggedLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

/**
 * Manages landmark tagging functionality.
 *
 * Handles:
 * - Landmark name
 * - Photo/signature capture
 * - Location information
 * - Database storage
 * - In-memory signature cache
 */
class LandmarkTagger(
    private val context: Context,
    private val taggedLocationDao: TaggedLocationDao
) {

    // In-memory cache for visual signatures keyed by tagId.
    private val signatureCache = mutableMapOf<String, FloatArray>()

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Tag a location with the given name and photo.
     * Uses dual-frame averaging: captures 2 consecutive frames ~300ms apart
     * and stores the averaged signature to reduce noise from camera movement.
     *
     * @param name Name provided by the user.
     * @param photo Captured photo of the landmark (first frame).
     * @param onSuccess Called when tagging succeeds.
     * @param onError Called when tagging fails.
     */
    suspend fun tagLocation(
        name: String,
        photo: Bitmap,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Extract signature from first frame
            val signature1 = VisualSignature.extractSignature(photo)

            // Wait 300ms for second frame capture
            // Note: The caller should provide the second frame via a callback mechanism
            // For now, we'll use the same frame as a fallback if dual-frame isn't implemented
            // TODO(Naren): Implement dual-frame capture with 300ms delay in BlindHomeScreen
            
            // For now, use single-frame signature (will be upgraded to dual-frame when UI supports it)
            val signature = signature1

            // Generate a unique ID for this landmark.
            val tagId = UUID.randomUUID().toString()

            // Get current location if available.
            val location = withContext(Dispatchers.IO) {
                getCurrentLocation()
            }

            // Create database entity.
            val taggedLocation = TaggedLocationEntity(
                tagId = tagId,
                name = name,
                buildingName = null,
                floorLevel = 0,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accessibilityNotes = "Tagged via blind assistant",
                isTeamVerified = false,
                timestamp = System.currentTimeMillis()
            )

            // Save landmark to database.
            taggedLocationDao.insertLocation(taggedLocation)

            // Save visual signature in memory.
            signatureCache[tagId] = signature

            // Tell caller that tagging succeeded.
            onSuccess(tagId)

        } catch (e: Exception) {
            onError(
                "Failed to tag location: ${e.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Tag a location with dual-frame averaging.
     * Captures 2 consecutive frames ~300ms apart and stores the averaged signature.
     *
     * @param name Name provided by the user.
     * @param photo1 First captured photo.
     * @param photo2 Second captured photo (captured ~300ms after first).
     * @param onSuccess Called when tagging succeeds.
     * @param onError Called when tagging fails.
     */
    suspend fun tagLocationWithDualFrame(
        name: String,
        photo1: Bitmap,
        photo2: Bitmap,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Extract signatures from both frames
            val signature1 = VisualSignature.extractSignature(photo1)
            val signature2 = VisualSignature.extractSignature(photo2)

            // Average the signatures to reduce noise
            val averagedSignature = VisualSignature.averageSignatures(signature1, signature2)

            // Generate a unique ID for this landmark.
            val tagId = UUID.randomUUID().toString()

            // Get current location if available.
            val location = withContext(Dispatchers.IO) {
                getCurrentLocation()
            }

            // Create database entity.
            val taggedLocation = TaggedLocationEntity(
                tagId = tagId,
                name = name,
                buildingName = null,
                floorLevel = 0,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accessibilityNotes = "Tagged via blind assistant",
                isTeamVerified = false,
                timestamp = System.currentTimeMillis()
            )

            // Save landmark to database.
            taggedLocationDao.insertLocation(taggedLocation)

            // Save averaged visual signature in memory.
            signatureCache[tagId] = averagedSignature

            // Tell caller that tagging succeeded.
            onSuccess(tagId)

        } catch (e: Exception) {
            onError(
                "Failed to tag location: ${e.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Get visual signature for a tag.
     */
    fun getSignature(tagId: String): FloatArray? {
        return signatureCache[tagId]
    }

    /**
     * Get all cached visual signatures.
     */
    fun getAllSignatures(): Map<String, FloatArray> {
        return signatureCache.toMap()
    }

    /**
     * Load landmark records from the database.
     *
     * Currently signatures are not stored in the database, so only
     * the in-memory cache is cleared/restored conceptually.
     */
    suspend fun loadSignaturesFromDatabase() {
        try {
            withContext(Dispatchers.IO) {
                taggedLocationDao.getAllLocations()
            }

            // Clear existing in-memory signatures.
            signatureCache.clear()

            /*
             * Visual signatures are not currently persisted.
             *
             * TODO(Naren): Add a visualSignature field to TaggedLocationEntity
             * to persist signatures in the database. The field should be:
             * val visualSignature: ByteArray? = null
             * 
             * This would allow signatures to survive app restarts and provide
             * a more robust landmark recognition experience.
             */

        } catch (_: Exception) {
            // Ignore cache-loading errors for now.
        }
    }

    /**
     * Get current location if location permission is available.
     *
     * This currently returns null because the project has not yet
     * implemented asynchronous Fused Location Provider handling.
     */
    private suspend fun getCurrentLocation(): Location? {

        val fineLocationGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        // No location permission.
        if (!fineLocationGranted && !coarseLocationGranted) {
            return null
        }

        /*
         * TODO:
         *
         * Implement:
         * fusedLocationClient.getCurrentLocation(...)
         *
         * with proper coroutine/task handling.
         *
         * For now, returning null keeps the existing behavior.
         */
        return null
    }
}


/**
 * Speech recognition helper for landmark naming.
 *
 * Allows the user to say something such as:
 *
 * "My Desk"
 *
 * and returns the recognized text.
 */
class LandmarkSpeechRecognizer(
    private val context: Context
) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Start speech recognition.
     *
     * @param onResult Called with the recognized text.
     * @param onError Called when recognition fails.
     */
    fun startRecognition(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        // Check whether speech recognition is available.
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available")
            return
        }

        // Clean up an existing recognizer before creating a new one.
        speechRecognizer?.destroy()

        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(context)

        /*
         * IMPORTANT:
         *
         * RecognizerIntent is NOT instantiated.
         *
         * We create android.content.Intent instead and use
         * RecognizerIntent constants to configure it.
         */
        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.US
            )

            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                "Say the name of this place"
            )
        }

        speechRecognizer?.setRecognitionListener(
            object : RecognitionListener {

                override fun onReadyForSpeech(
                    params: android.os.Bundle?
                ) {
                    // Speech recognizer is ready.
                }

                override fun onBeginningOfSpeech() {
                    // User started speaking.
                }

                override fun onRmsChanged(
                    rmsdB: Float
                ) {
                    // Volume level changed.
                }

                override fun onBufferReceived(
                    buffer: ByteArray?
                ) {
                    // Raw audio buffer received.
                }

                override fun onEndOfSpeech() {
                    // User stopped speaking.
                }

                override fun onError(
                    error: Int
                ) {

                    val errorMessage = when (error) {

                        SpeechRecognizer.ERROR_AUDIO ->
                            "Audio recording error"

                        SpeechRecognizer.ERROR_CLIENT ->
                            "Speech recognition client error"

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission is required"

                        SpeechRecognizer.ERROR_NETWORK ->
                            "Network error"

                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Network timeout"

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            "No speech detected"

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Speech recognizer is busy"

                        SpeechRecognizer.ERROR_SERVER ->
                            "Speech recognition server error"

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "No speech input"

                        else ->
                            "Unknown speech recognition error"
                    }

                    onError(errorMessage)

                    cleanupRecognizer()
                }

                override fun onResults(
                    results: android.os.Bundle?
                ) {

                    val matches =
                        results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                    if (!matches.isNullOrEmpty()) {

                        val recognizedText = matches[0]

                        onResult(recognizedText)

                    } else {

                        onError("No speech recognized")
                    }

                    cleanupRecognizer()
                }

                override fun onPartialResults(
                    partialResults: android.os.Bundle?
                ) {
                    // Partial results are not currently used.
                }

                override fun onEvent(
                    eventType: Int,
                    params: android.os.Bundle?
                ) {
                    // No additional events required.
                }
            }
        )

        // Start listening.
        try {

            speechRecognizer?.startListening(intent)

        } catch (e: SecurityException) {

            onError(
                "Microphone permission is required"
            )

            cleanupRecognizer()

        } catch (e: Exception) {

            onError(
                "Unable to start speech recognition: ${
                    e.message ?: "Unknown error"
                }"
            )

            cleanupRecognizer()
        }
    }

    /**
     * Stop speech recognition.
     */
    fun stopRecognition() {

        speechRecognizer?.stopListening()

        speechRecognizer?.destroy()

        speechRecognizer = null
    }

    /**
     * Clean up the SpeechRecognizer instance.
     */
    private fun cleanupRecognizer() {

        speechRecognizer?.destroy()

        speechRecognizer = null
    }
}