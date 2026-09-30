package com.teamdexters.limitless.ui.blind

import android.graphics.Bitmap
import com.teamdexters.limitless.data.local.dao.TaggedLocationDao
import com.teamdexters.limitless.data.local.entity.TaggedLocationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages landmark recognition functionality.
 * Compares current frame signature against stored landmark signatures using strict matching.
 */
class LandmarkRecognizer(
    private val taggedLocationDao: TaggedLocationDao,
    private val signatureCache: Map<String, FloatArray>
) {

    private companion object {
        const val SIMILARITY_THRESHOLD = 50f // Minimum similarity score (50%) — forgiving match under normal room lighting
    }

    /**
     * Recognize a landmark from the current camera frame.
     * Uses forgiving matching: similarity >= 55% without strict margin requirements.
     * @param currentFrame The current camera frame
     * @return Recognition result with appropriate feedback message
     */
    suspend fun recognizeLandmark(currentFrame: Bitmap): RecognitionResult {
        return withContext(Dispatchers.IO) {
            try {
                // Extract signature from current frame
                val currentSignature = VisualSignature.extractSignature(currentFrame)
                
                // If no signatures cached, return no match
                if (signatureCache.isEmpty()) {
                    return@withContext RecognitionResult.NoMatch(
                        "No saved place recognized here. Try matching the original view."
                    )
                }
                
                // Compute similarities for all cached signatures
                val similarities = mutableListOf<Pair<String, Float>>()
                
                for ((tagId, signature) in signatureCache) {
                    val distance = VisualSignature.compareSignatures(currentSignature, signature)
                    val similarity = VisualSignature.distanceToSimilarity(distance)
                    similarities.add(Pair(tagId, similarity))
                }
                
                // Sort by similarity (descending)
                similarities.sortByDescending { it.second }
                
                // Check if we have at least one match
                if (similarities.isEmpty()) {
                    return@withContext RecognitionResult.NoMatch(
                        "No saved place recognized here. Try matching the original view."
                    )
                }
                
                val topMatch = similarities[0]
                val topSimilarity = topMatch.second
                
                // Check if top similarity meets threshold (55%)
                if (topSimilarity < SIMILARITY_THRESHOLD) {
                    return@withContext RecognitionResult.NoMatch(
                        "No saved place recognized here. Try matching the original view."
                    )
                }
                
                // Get location name from database
                val location = taggedLocationDao.getLocationByTag(topMatch.first)
                if (location != null) {
                    RecognitionResult.Success(
                        location.name,
                        "You are near: ${location.name}"
                    )
                } else {
                    RecognitionResult.NoMatch(
                        "No saved place recognized here. Try matching the original view."
                    )
                }
            } catch (e: Exception) {
                RecognitionResult.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Recognition result sealed class with spoken feedback messages.
     */
    sealed class RecognitionResult {
        data class Success(val locationName: String, val message: String) : RecognitionResult()
        data class NoMatch(val message: String) : RecognitionResult()
        data class Error(val message: String) : RecognitionResult()
    }
}
