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
        const val SIMILARITY_THRESHOLD = 70f // Minimum similarity score (70%)
        const val MARGIN_THRESHOLD = 10f // Minimum margin between top and second match (10%)
    }

    /**
     * Recognize a landmark from the current camera frame.
     * Uses strict matching: similarity >= 70% and top match beats second by >= 10% margin.
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
                        "No tagged place recognized. Make sure lighting and angle match."
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
                        "No tagged place recognized. Make sure lighting and angle match."
                    )
                }
                
                val topMatch = similarities[0]
                val topSimilarity = topMatch.second
                
                // Check if top similarity meets threshold
                if (topSimilarity < SIMILARITY_THRESHOLD) {
                    return@withContext RecognitionResult.NoMatch(
                        "No tagged place recognized. Make sure lighting and angle match."
                    )
                }
                
                // Check margin requirement if we have at least 2 matches
                if (similarities.size >= 2) {
                    val secondMatch = similarities[1]
                    val secondSimilarity = secondMatch.second
                    val margin = topSimilarity - secondSimilarity
                    
                    if (margin < MARGIN_THRESHOLD) {
                        return@withContext RecognitionResult.Ambiguous(
                            "Multiple similar places detected. Try moving closer."
                        )
                    }
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
                        "No tagged place recognized. Make sure lighting and angle match."
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
        data class Ambiguous(val message: String) : RecognitionResult()
        data class Error(val message: String) : RecognitionResult()
    }
}
