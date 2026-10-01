package com.teamdexters.limitless.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val VOWELS = setOf('a', 'e', 'i', 'o', 'u')

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        Log.e("HapticManager", "Failed to initialize Vibrator: ${e.message}")
        null
    }
    
    private val hasVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    /**
     * Dynamic Haptic Vocabulary: vibrates the user's name letter-by-letter.
     * Vowels   → 300ms pulse + 100ms gap (Tactile Long)
     * Consonants/Digits → 100ms pulse + 50ms gap (Tactile Short)
     * An initial 0ms delay is prepended as required by VibrationEffect.createWaveform.
     */
    fun playNameRhythm(name: String) {
        Log.d("HapticManager", "=== HAPTIC_START ===")
        Log.d("HapticManager", "HAPTIC: Matched name='$name'")
        Log.d("HapticManager", "HAPTIC: API level=${Build.VERSION.SDK_INT}")
        Log.d("HapticManager", "HAPTIC: Has vibrator=$hasVibrator")
        
        if (!hasVibrator) {
            Log.e("HapticManager", "HAPTIC: FAILURE - No vibrator available")
            return
        }
        
        val cleaned = name.lowercase().trim()
        if (cleaned.isEmpty()) {
            Log.w("HapticManager", "HAPTIC: FAILURE - Empty name")
            return
        }

        Log.d("HapticManager", "HAPTIC: Cleaned name='$cleaned'")

        // Build interleaved [delay, pulse, gap, pulse, gap, ...] array
        val timings = mutableListOf<Long>(0L) // leading 0 required by waveform API
        for (ch in cleaned) {
            if (ch == ' ') continue
            if (ch in VOWELS) {
                timings.add(300L) // vowel pulse
                timings.add(100L) // inter-character gap
                Log.d("HapticManager", "HAPTIC_PATTERN: Vowel '$ch' -> 300ms pulse, 100ms gap")
            } else {
                timings.add(100L) // consonant/digit pulse
                timings.add(50L)  // inter-character gap
                Log.d("HapticManager", "HAPTIC_PATTERN: Consonant '$ch' -> 100ms pulse, 50ms gap")
            }
        }

        val pattern = timings.toLongArray()
        val totalDuration = pattern.sum()
        Log.d("HapticManager", "HAPTIC: Pattern length=${pattern.size}, Total duration=${totalDuration}ms")
        
        playPattern(pattern)
        
        Log.d("HapticManager", "HAPTIC: SUCCESS - Vibration triggered")
        Log.d("HapticManager", "=== HAPTIC_END ===")
    }

    /** Legacy fixed pattern — kept for backward compatibility. */
    fun playNameCallPattern() {
        Log.d("HapticManager", "=== HAPTIC_START ===")
        Log.d("HapticManager", "HAPTIC: Pattern=NameCallPattern")
        val pattern = longArrayOf(0, 100, 50, 100, 50, 100)
        playPattern(pattern)
        Log.d("HapticManager", "=== HAPTIC_END ===")
    }

    private fun playPattern(pattern: LongArray) {
        Log.d("HapticManager", "HAPTIC_PATTERN: Playing pattern with ${pattern.size} timings")
        
        if (!hasVibrator) {
            Log.e("HapticManager", "HAPTIC_PATTERN: FAILURE - No vibrator available")
            return
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator?.vibrate(effect)
                Log.d("HapticManager", "HAPTIC_PATTERN: SUCCESS - VibrationEffect created (API ${Build.VERSION.SDK_INT})")
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
                Log.d("HapticManager", "HAPTIC_PATTERN: SUCCESS - Legacy vibration (API ${Build.VERSION.SDK_INT})")
            }
        } catch (e: SecurityException) {
            Log.e("HapticManager", "HAPTIC_PATTERN: FAILURE - Security exception (missing VIBRATE permission): ${e.message}")
        } catch (e: Exception) {
            Log.e("HapticManager", "HAPTIC_PATTERN: FAILURE - Exception: ${e.message}", e)
        }
    }

    fun playDangerPattern() {
        Log.d("HapticManager", "=== HAPTIC_START ===")
        Log.d("HapticManager", "HAPTIC: Pattern=DangerPattern")
        val pattern = longArrayOf(0, 1500, 500, 1500)
        playPattern(pattern)
        Log.d("HapticManager", "=== HAPTIC_END ===")
    }

    fun playDoorPattern() {
        Log.d("HapticManager", "=== HAPTIC_START ===")
        Log.d("HapticManager", "HAPTIC: Pattern=DoorPattern")
        val pattern = longArrayOf(0, 200, 100, 200)
        playPattern(pattern)
        Log.d("HapticManager", "=== HAPTIC_END ===")
    }

    fun playApplausePattern() {
        Log.d("HapticManager", "=== HAPTIC_START ===")
        Log.d("HapticManager", "HAPTIC: Pattern=ApplausePattern")
        val pattern = longArrayOf(0, 50, 50, 50, 50, 50, 50)
        playPattern(pattern)
        Log.d("HapticManager", "=== HAPTIC_END ===")
    }

    fun playSOSLoopPattern() {
        Log.d("HapticManager", "=== HAPTIC_START ===")
        Log.d("HapticManager", "HAPTIC: Pattern=SOSLoopPattern (looping)")
        val pattern = longArrayOf(0, 1000, 200, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Repeat from index 1 to keep looping [1000, 200, 1000, 200...]
            val effect = VibrationEffect.createWaveform(pattern, 1)
            vibrator?.vibrate(effect)
            Log.d("HapticManager", "HAPTIC_PATTERN: VibrationEffect created with loop (API 26+)")
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 1)
            Log.d("HapticManager", "HAPTIC_PATTERN: Legacy vibration with loop (pre-API 26)")
        }
        Log.d("HapticManager", "=== HAPTIC_END ===")
    }

    fun stop() {
        Log.d("HapticManager", "=== HAPTIC_STOP ===")
        Log.d("HapticManager", "HAPTIC: Cancelling all vibrations")
        vibrator?.cancel()
    }
}
