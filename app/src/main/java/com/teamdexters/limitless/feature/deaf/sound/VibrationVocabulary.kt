package com.teamdexters.limitless.feature.deaf.sound

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * Vibration vocabulary system for distinct sound-type patterns.
 * Wraps Vibrator/VibratorManager with SDK 26+ safe vibration effects.
 */
class VibrationVocabulary(private val context: Context?) {
    
    private val vibrator: Vibrator? = try {
        if (context == null) null
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }
    
    /**
     * Sound types with corresponding vibration patterns.
     */
    enum class SoundType {
        SIREN,
        FIRE_ALARM,
        DOORBELL,
        DOG_BARKING,
        BABY_CRYING,
        CAR_HORN
    }
    
    /**
     * Vibrate for a specific sound type with the corresponding pattern.
     */
    fun vibrateForSound(soundType: SoundType) {
        try {
            if (!hasVibrator()) {
                return
            }
            
            when (soundType) {
                SoundType.SIREN -> vibrateSiren()
                SoundType.FIRE_ALARM -> vibrateFireAlarm()
                SoundType.DOORBELL -> vibrateDoorbell()
                SoundType.DOG_BARKING -> vibrateDogBarking()
                SoundType.BABY_CRYING -> vibrateBabyCrying()
                SoundType.CAR_HORN -> vibrateCarHorn()
            }
        } catch (e: Exception) {
            // Ignore vibration errors - don't crash if vibration fails
        }
    }
    
    /**
     * Siren pattern: Long-short-long-short pulse with USAGE_ALARM (overrides Silent/DND).
     */
    private fun vibrateSiren() {
        val pattern = longArrayOf(0, 600, 150, 200, 150, 600)
        vibrateWithPattern(pattern, createAlarmAttributes())
    }
    
    /**
     * Fire Alarm pattern: 3 sharp bursts with USAGE_ALARM.
     */
    private fun vibrateFireAlarm() {
        val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
        vibrateWithPattern(pattern, createAlarmAttributes())
    }
    
    /**
     * Doorbell pattern: 2 medium bursts with USAGE_NOTIFICATION.
     */
    private fun vibrateDoorbell() {
        val pattern = longArrayOf(0, 300, 150, 300)
        vibrateWithPattern(pattern, createNotificationAttributes())
    }
    
    /**
     * Dog Barking pattern: 4 short taps with USAGE_NOTIFICATION.
     */
    private fun vibrateDogBarking() {
        val pattern = longArrayOf(0, 100, 80, 100, 80, 100, 80, 100)
        vibrateWithPattern(pattern, createNotificationAttributes())
    }
    
    /**
     * Baby Crying pattern: Continuous slow pulse with USAGE_NOTIFICATION.
     */
    private fun vibrateBabyCrying() {
        val pattern = longArrayOf(0, 500, 200, 500, 200)
        vibrateWithPattern(pattern, createNotificationAttributes())
    }
    
    /**
     * Car Horn pattern: 1 long burst with USAGE_NOTIFICATION.
     */
    private fun vibrateCarHorn() {
        val pattern = longArrayOf(0, 800)
        vibrateWithPattern(pattern, createNotificationAttributes())
    }
    
    /**
     * Vibrate with a specific pattern and audio attributes.
     */
    private fun vibrateWithPattern(pattern: LongArray, audioAttributes: AudioAttributes) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(effect, audioAttributes)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
        }
    }
    
    /**
     * Create alarm audio attributes (overrides Silent/DND).
     */
    private fun createAlarmAttributes(): AudioAttributes {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            // Fallback for older SDKs
            AudioAttributes.Builder().build()
        }
    }
    
    /**
     * Create notification audio attributes.
     */
    private fun createNotificationAttributes(): AudioAttributes {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            // Fallback for older SDKs
            AudioAttributes.Builder().build()
        }
    }
    
    /**
     * Check if device has a vibrator.
     */
    private fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }
    
    /**
     * Cancel any ongoing vibration.
     */
    fun cancel() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // Ignore cancel errors
        }
    }
}
