package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import com.teamdexters.limitless.data.local.dao.TaggedLocationDao
import com.teamdexters.limitless.data.local.entity.TaggedLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages landmark recognition functionality.
 * Compares current frame signature against stored landmark signatures.
 */
class LandmarkRecognizer(
    private val taggedLocationDao: TaggedLocationDao,
    private val signatureCache: Map<String, FloatArray>
) {

    private companion object {
        const val SIMILARITY_THRESHOLD = 2.0f // Maximum Euclidean distance for match
    }

    /**
     * Recognize a landmark from the current camera frame.
     * @param currentFrame The current camera frame
     * @return Recognition result with matched location name or null if no match
     */
    suspend fun recognizeLandmark(currentFrame: Bitmap): RecognitionResult {
        return withContext(Dispatchers.IO) {
            try {
                // Extract signature from current frame
                val currentSignature = VisualSignature.extractSignature(currentFrame)
                
                // Find best match among cached signatures
                var bestMatch: Pair<String, Float>? = null
                var bestDistance = Float.MAX_VALUE
                
                for ((tagId, signature) in signatureCache) {
                    val distance = VisualSignature.compareSignatures(currentSignature, signature)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestMatch = Pair(tagId, distance)
                    }
                }
                
                // Check if best match exceeds threshold
                if (bestMatch != null && bestMatch.second < SIMILARITY_THRESHOLD) {
                    // Get location name from database
                    val location = taggedLocationDao.getLocationByTag(bestMatch.first)
                    if (location != null) {
                        RecognitionResult.Success(location.name)
                    } else {
                        RecognitionResult.NoMatch
                    }
                } else {
                    RecognitionResult.NoMatch
                }
            } catch (e: Exception) {
                RecognitionResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Recognition result sealed class.
     */
    sealed class RecognitionResult {
        data class Success(val locationName: String) : RecognitionResult()
        object NoMatch : RecognitionResult()
        data class Error(val message: String) : RecognitionResult()
    }
}
