package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Haptic Vocabulary System for spatial awareness feedback.
 * Provides distinct vibration patterns for different spatial events.
 */
object HapticVocabulary {

    /**
     * Sealed class representing all haptic events.
     */
    sealed class HapticEvent {
        data object OBJECT_LEFT : HapticEvent()
        data object OBJECT_RIGHT : HapticEvent()
        data object OBJECT_CENTER : HapticEvent()
        data object OBSTACLE_NEAR : HapticEvent()
        data object RAMP_DETECTED : HapticEvent()
        data object STAIRS_DETECTED : HapticEvent()
        data object DOOR_OPEN : HapticEvent()
        data object DESTINATION_REACHED : HapticEvent()
    }

    /**
     * Trigger a haptic event with the appropriate vibration pattern.
     * @param context Android context
     * @param event The haptic event to trigger
     */
    fun trigger(context: Context, event: HapticEvent) {
        val vibrator = getVibrator(context) ?: return

        if (!vibrator.hasVibrator()) {
            return
        }

        val pattern = when (event) {
            HapticEvent.OBJECT_LEFT -> OBJECT_LEFT_PATTERN
            HapticEvent.OBJECT_RIGHT -> OBJECT_RIGHT_PATTERN
            HapticEvent.OBJECT_CENTER -> OBJECT_CENTER_PATTERN
            HapticEvent.OBSTACLE_NEAR -> OBSTACLE_NEAR_PATTERN
            HapticEvent.RAMP_DETECTED -> RAMP_DETECTED_PATTERN
            HapticEvent.STAIRS_DETECTED -> STAIRS_DETECTED_PATTERN
            HapticEvent.DOOR_OPEN -> DOOR_OPEN_PATTERN
            HapticEvent.DESTINATION_REACHED -> DESTINATION_REACHED_PATTERN
        }

        // Cancel any ongoing vibration before triggering new one
        vibrator.cancel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            // Fallback for API < 26
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    /**
     * Trigger a one-shot haptic pulse (e.g., for response ready feedback).
     * @param context Android context
     * @param durationMs Duration of the pulse in milliseconds
     */
    fun triggerOneShot(context: Context, durationMs: Long) {
        val vibrator = getVibrator(context) ?: return

        if (!vibrator.hasVibrator()) {
            return
        }

        // Cancel any ongoing vibration before triggering new one
        vibrator.cancel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        } else {
            // Fallback for API < 26
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    /**
     * Get the Vibrator service (handles API 31+ changes).
     */
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    // Vibration patterns (timings in milliseconds)
    // Format: [off, on, off, on, ...]

    // Object on LEFT: 2 short pulses
    private val OBJECT_LEFT_PATTERN = longArrayOf(0, 100, 80, 100)

    // Object on RIGHT: 3 short pulses
    private val OBJECT_RIGHT_PATTERN = longArrayOf(0, 100, 80, 100, 80, 100)

    // Object CENTER / AHEAD: 1 long pulse
    private val OBJECT_CENTER_PATTERN = longArrayOf(0, 300)

    // Obstacle within ~1m (large bbox, >0.35 of frame): rapid triple pulse (danger)
    private val OBSTACLE_NEAR_PATTERN = longArrayOf(0, 80, 60, 80, 60, 80)

    // Ramp detected: 3-tap ascending
    private val RAMP_DETECTED_PATTERN = longArrayOf(0, 60, 60, 100, 60, 140)

    // Stairs detected: 3 sharp equal pulses
    private val STAIRS_DETECTED_PATTERN = longArrayOf(0, 120, 100, 120, 100, 120)

    // Door open ahead: soft double buzz
    private val DOOR_OPEN_PATTERN = longArrayOf(0, 80, 120, 80)

    // Destination reached: 5 quick celebratory taps
    private val DESTINATION_REACHED_PATTERN = longArrayOf(0, 60, 60, 60, 60, 60, 60, 60, 60, 60)
}
