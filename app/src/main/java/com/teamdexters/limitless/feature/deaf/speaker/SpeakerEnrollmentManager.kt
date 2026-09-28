package com.teamdexters.limitless.feature.deaf.speaker

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manager for speaker enrollment and identification.
 * 
 * Features:
 * - Voice enrollment: Record 3-5 seconds of 16kHz audio samples
 * - Feature extraction: RMS energy, spectral centroid, pitch profile
 * - Matching: Compare incoming audio against enrolled profiles
 * - Local storage: All profiles stored locally (no cloud APIs)
 */
class SpeakerEnrollmentManager(private val context: Context) {
    
    private val _enrolledProfiles = MutableStateFlow<List<VoiceProfile>>(emptyList())
    val enrolledProfiles: StateFlow<List<VoiceProfile>> = _enrolledProfiles.asStateFlow()
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    
    private val _recordingProgress = MutableStateFlow(0f)
    val recordingProgress: StateFlow<Float> = _recordingProgress.asStateFlow()
    
    private val _enrollmentStatus = MutableStateFlow<EnrollmentStatus>(EnrollmentStatus.Idle)
    val enrollmentStatus: StateFlow<EnrollmentStatus> = _enrollmentStatus.asStateFlow()
    
    private val audioRecordBufferSize = 4096
    private val sampleRate = 16000
    private val recordingDurationMs = 4000L // 4 seconds
    private val matchThreshold = 0.15f // Threshold for speaker matching
    
    enum class EnrollmentStatus {
        Idle,
        Recording,
        Processing,
        Success,
        Error
    }
    
    /**
     * Start voice enrollment recording.
     * Records 4 seconds of audio at 16kHz.
     */
    suspend fun startEnrollment(name: String): VoiceProfile? {
        return withContext(Dispatchers.Main) {
            try {
                _isRecording.value = true
                _enrollmentStatus.value = EnrollmentStatus.Recording
                
                val audioData = withContext(Dispatchers.IO) { recordAudio() }
                
                if (audioData.isEmpty()) {
                    _enrollmentStatus.value = EnrollmentStatus.Error
                    _isRecording.value = false
                    return@withContext null
                }
                
                _enrollmentStatus.value = EnrollmentStatus.Processing
                
                val features = extractFeatures(audioData)
                val profile = VoiceProfile(
                    id = VoiceProfile.generateId(),
                    name = name,
                    rmsEnergy = features.rmsEnergy,
                    spectralCentroid = features.spectralCentroid,
                    pitchProfile = features.pitchProfile
                )
                
                val updatedProfiles = _enrolledProfiles.value.toMutableList()
                updatedProfiles.add(profile)
                _enrolledProfiles.value = updatedProfiles
                
                _enrollmentStatus.value = EnrollmentStatus.Success
                _isRecording.value = false
                
                profile
            } catch (e: Exception) {
                _enrollmentStatus.value = EnrollmentStatus.Error
                _isRecording.value = false
                null
            }
        }
    }
    
    /**
     * Record audio using AudioRecord.
     */
    private suspend fun recordAudio(): ShortArray {
        return withContext(Dispatchers.IO) {
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2
            
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            try {
                audioRecord.startRecording()
                
                val audioData = mutableListOf<Short>()
                val buffer = ShortArray(bufferSize / 2)
                val startTime = System.currentTimeMillis()
                
                while (System.currentTimeMillis() - startTime < recordingDurationMs) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        audioData.addAll(buffer.take(read))
                    }
                    
                    val progress = ((System.currentTimeMillis() - startTime).toFloat() / recordingDurationMs).coerceIn(0f, 1f)
                    _recordingProgress.value = progress
                }
                
                audioData.toShortArray()
            } finally {
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }
    
    /**
     * Extract audio features for speaker identification.
     */
    private fun extractFeatures(audioData: ShortArray): AudioFeatures {
        var sum = 0.0
        var sumSquares = 0.0
        
        for (sample in audioData) {
            val normalized = sample / 32768.0
            sum += abs(normalized)
            sumSquares += normalized * normalized
        }
        
        val rmsEnergy = sqrt(sumSquares / audioData.size).toFloat()
        
        // Simplified spectral centroid (approximate using energy distribution)
        val spectralCentroid = calculateSpectralCentroid(audioData)
        
        // Simplified pitch profile (zero-crossing rate approximation)
        val pitchProfile = calculatePitchProfile(audioData)
        
        return AudioFeatures(
            rmsEnergy = rmsEnergy,
            spectralCentroid = spectralCentroid,
            pitchProfile = pitchProfile
        )
    }
    
    /**
     * Calculate spectral centroid (frequency center of mass).
     */
    private fun calculateSpectralCentroid(audioData: ShortArray): Float {
        var weightedSum = 0.0
        var sum = 0.0
        
        for (i in audioData.indices) {
            val magnitude = abs(audioData[i].toDouble())
            weightedSum += i * magnitude
            sum += magnitude
        }
        
        return if (sum > 0) (weightedSum / sum).toFloat() else 0f
    }
    
    /**
     * Calculate pitch profile using zero-crossing rate.
     */
    private fun calculatePitchProfile(audioData: ShortArray): Float {
        var zeroCrossings = 0
        
        for (i in 1 until audioData.size) {
            if ((audioData[i] >= 0) != (audioData[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        
        return (zeroCrossings.toFloat() / audioData.size) * sampleRate
    }
    
    /**
     * Match incoming audio against enrolled profiles.
     * Returns the best matching profile if confidence exceeds threshold.
     */
    suspend fun matchSpeaker(audioData: ShortArray): VoiceProfile? {
        return withContext(Dispatchers.IO) {
            if (_enrolledProfiles.value.isEmpty()) {
                return@withContext null
            }
            
            val features = extractFeatures(audioData)
            var bestMatch: VoiceProfile? = null
            var bestDistance = Float.MAX_VALUE
            
            for (profile in _enrolledProfiles.value) {
                val distance = calculateFeatureDistance(features, profile)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestMatch = profile
                }
            }
            
            if (bestDistance < matchThreshold) {
                bestMatch
            } else {
                null
            }
        }
    }
    
    /**
     * Calculate Euclidean distance between audio features and profile.
     */
    private fun calculateFeatureDistance(features: AudioFeatures, profile: VoiceProfile): Float {
        val rmsDiff = features.rmsEnergy - profile.rmsEnergy
        val spectralDiff = features.spectralCentroid - profile.spectralCentroid
        val pitchDiff = features.pitchProfile - profile.pitchProfile
        
        return sqrt(rmsDiff * rmsDiff + spectralDiff * spectralDiff + pitchDiff * pitchDiff)
    }
    
    /**
     * Delete a voice profile by ID.
     */
    fun deleteProfile(profileId: String) {
        val updatedProfiles = _enrolledProfiles.value.filter { it.id != profileId }
        _enrolledProfiles.value = updatedProfiles
    }
    
    /**
     * Clear all enrolled profiles.
     */
    fun clearAllProfiles() {
        _enrolledProfiles.value = emptyList()
    }
    
    /**
     * Reset enrollment status.
     */
    fun resetEnrollmentStatus() {
        _enrollmentStatus.value = EnrollmentStatus.Idle
        _recordingProgress.value = 0f
    }
    
    private data class AudioFeatures(
        val rmsEnergy: Float,
        val spectralCentroid: Float,
        val pitchProfile: Float
    )
}
