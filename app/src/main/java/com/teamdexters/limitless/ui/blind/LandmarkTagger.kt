package com.teamdexters.limitless.ui.blind

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.teamdexters.limitless.data.local.dao.TaggedLocationDao
import com.teamdexters.limitless.data.local.entity.TaggedLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Manages landmark tagging functionality.
 * Handles speech recognition, text input fallback, photo capture, and database storage.
 */
class LandmarkTagger(
    private val context: Context,
    private val taggedLocationDao: TaggedLocationDao
) {
    // In-memory cache for signatures keyed by tagId
    private val signatureCache = mutableMapOf<String, FloatArray>()
    
    private val fusedLocationClient: FusedLocationProviderClient = 
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Tag a location with the given name and photo.
     * @param name The name provided by the user (via speech or text)
     * @param photo The captured photo
     * @param onSuccess Callback when tagging succeeds
     * @param onError Callback when tagging fails
     */
    suspend fun tagLocation(
        name: String,
        photo: Bitmap,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Extract visual signature
            val signature = VisualSignature.extractSignature(photo)
            
            // Generate unique tagId
            val tagId = UUID.randomUUID().toString()
            
            // Get current location if available
            val location = withContext(Dispatchers.IO) {
                getCurrentLocation()
            }
            
            // Create TaggedLocationEntity
            val taggedLocation = TaggedLocationEntity(
                tagId = tagId,
                name = name,
                buildingName = null, // TODO(Naren): need buildingName input UI if user wants to specify building
                floorLevel = 0,
                latitude = location?.latitude,
                longitude = location?.longitude,
                accessibilityNotes = "Tagged via blind assistant",
                isTeamVerified = false,
                timestamp = System.currentTimeMillis()
            )
            
            // Save to database
            taggedLocationDao.insertLocation(taggedLocation)
            
            // Cache signature
            signatureCache[tagId] = signature
            
            onSuccess(tagId)
        } catch (e: Exception) {
            onError("Failed to tag location: ${e.message}")
        }
    }

    /**
     * Get signature for a given tagId from cache.
     * @param tagId The tag identifier
     * @return The signature if found, null otherwise
     */
    fun getSignature(tagId: String): FloatArray? {
        return signatureCache[tagId]
    }

    /**
     * Get all cached signatures.
     * @return Map of tagId to signature
     */
    fun getAllSignatures(): Map<String, FloatArray> {
        return signatureCache.toMap()
    }

    /**
     * Load signatures from database into cache.
     * Call this on app startup to restore the cache.
     */
    suspend fun loadSignaturesFromDatabase() {
        try {
            val locations = withContext(Dispatchers.IO) {
                taggedLocationDao.getAllLocations()
            }
            
            // Clear existing cache
            signatureCache.clear()
            
            // Note: We can't extract signatures from stored entities since we don't store the photos
            // In a real implementation, we would store the signatures in the entity or in a separate table
            // For this phase, the cache only persists during the app session
        } catch (e: Exception) {
            // Ignore errors during cache loading
        }
    }

    /**
     * Get current location if available.
     * @return Location object or null if unavailable
     */
    private suspend fun getCurrentLocation(): Location? {
        // Check location permissions
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        // TODO(Naren): need proper async location handling using fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        // The current implementation returns null to avoid complexity with suspend functions
        return null
    }
}

/**
 * Speech recognition helper for landmark naming.
 */
class LandmarkSpeechRecognizer(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Start speech recognition for landmark naming.
     * @param onResult Callback with recognized text
     * @param onError Callback for errors
     */
    fun startRecognition(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            
            val intent = RecognizerIntent().apply {
                action = RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.US)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the name of this place")
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error"
                    }
                    onError(errorMessage)
                    speechRecognizer?.destroy()
                }

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        onResult(matches[0])
                    } else {
                        onError("No speech recognized")
                    }
                    speechRecognizer?.destroy()
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })

            speechRecognizer?.startListening(intent)
        } else {
            onError("Speech recognition not available")
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
}
