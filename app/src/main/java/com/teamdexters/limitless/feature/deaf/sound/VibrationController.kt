package com.teamdexters.limitless.feature.deaf.sound

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat

/**
 * Controller for vibration patterns for different sound types.
 * Provides distinct vibration patterns for emergency and notification sounds.
 */
class VibrationController(private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if vibration permission is granted.
     */
    fun hasVibrationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if device has vibration capability.
     */
    fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }

    /**
     * Trigger vibration pattern for the given sound type.
     * @param soundType Type of sound detected
     */
    fun vibrate(soundType: SoundType) {
        try {
            if (!hasVibrator()) {
                return
            }

            val vibrationEffect = when (soundType) {
                SoundType.FIRE_ALARM -> createFireAlarmPattern()
                SoundType.SIREN -> createSirenPattern()
                SoundType.DOORBELL -> createDoorbellPattern()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = when (soundType) {
                    SoundType.FIRE_ALARM, SoundType.SIREN -> {
                        // Emergency sounds - override silent/DND
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    SoundType.DOORBELL -> {
                        // Notification sound
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                }
                
                vibrator?.vibrate(vibrationEffect, audioAttributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(vibrationEffect)
            }
        } catch (e: Exception) {
            // Never crash on vibration
        }
    }

    /**
     * Fire alarm vibration pattern: Fast pulsing staccato.
     * Pattern: Short on-off bursts for urgency.
     */
    private fun createFireAlarmPattern(): VibrationEffect {
        // Fast staccato: 50ms on, 50ms off, repeated
        val timings = longArrayOf(
            0, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
            200, // Pause between bursts
            50, 50, 50, 50, 50, 50, 50, 50, 50, 50,
            200,
            50, 50, 50, 50, 50, 50, 50, 50, 50, 50
        )
        val amplitudes = intArrayOf(
            0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0,
            0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0,
            0,
            255, 0, 255, 0, 255, 0, 255, 0, 255, 0
        )
        
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    /**
     * Siren vibration pattern: Long continuous pulses.
     * Pattern: Longer pulses with gaps to simulate siren rhythm.
     */
    private fun createSirenPattern(): VibrationEffect {
        // Siren rhythm: 200ms on, 100ms off, 200ms on, 400ms off
        val timings = longArrayOf(
            0, 200, 100, 200, 400,
            200, 100, 200, 400,
            200, 100, 200, 400
        )
        val amplitudes = intArrayOf(
            0, 255, 0, 255, 0,
            255, 0, 255, 0,
            255, 0, 255, 0
        )
        
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    /**
     * Doorbell vibration pattern: Gentle two-tap pattern.
     * Pattern: Two distinct taps with a gap, repeated once.
     */
    private fun createDoorbellPattern(): VibrationEffect {
        // Doorbell: Ding-dong pattern
        val timings = longArrayOf(
            0, 100, 50, 150, 300,
            100, 50, 150
        )
        val amplitudes = intArrayOf(
            0, 200, 0, 200, 0,
            200, 0, 200
        )
        
        return VibrationEffect.createWaveform(timings, amplitudes, -1)
    }

    /**
     * Cancel any ongoing vibration.
     */
    fun cancel() {
        vibrator?.cancel()
    }
}