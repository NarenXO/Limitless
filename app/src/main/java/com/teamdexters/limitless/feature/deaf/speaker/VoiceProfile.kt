package com.teamdexters.limitless.feature.deaf.speaker

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Voice profile data class for speaker identification.
 * Contains audio feature vectors and speaker metadata.
 */
@Parcelize
data class VoiceProfile(
    val id: String,
    val name: String,
    val rmsEnergy: Float,
    val spectralCentroid: Float,
    val pitchProfile: Float,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {
    
    companion object {
        fun generateId(): String {
            return "voice_${System.currentTimeMillis()}_${(0..9999).random()}"
        }
    }
}
