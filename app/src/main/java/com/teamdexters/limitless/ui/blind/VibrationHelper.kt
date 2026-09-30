package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Helper class for generating vibration patterns for turn-by-turn navigation.
 * Provides distinct vibration patterns for each turn type.
 */
class VibrationHelper(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Trigger vibration pattern based on turn type.
     * @param turnType The type of turn to vibrate for
     */
    fun vibrateForTurn(turnType: TurnType) {
        if (!hasVibrator()) return

        val pattern = when (turnType) {
            TurnType.LEFT -> createLeftPattern()
            TurnType.RIGHT -> createRightPattern()
            TurnType.STRAIGHT -> createStraightPattern()
            TurnType.ARRIVE -> createArrivePattern()
        }

        vibrator?.vibrate(pattern)
    }

    /**
     * Check if device has a vibrator.
     */
    private fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }

    /**
     * LEFT: 2 short pulses (100ms pulse, 200ms pause, 100ms pulse)
     */
    private fun createLeftPattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(longArrayOf(0, 100, 200, 100), -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createWaveform(longArrayOf(0, 100, 200, 100), -1)
        }
    }

    /**
     * RIGHT: 3 short pulses (100ms pulse, 150ms pause, 100ms pulse, 150ms pause, 100ms pulse)
     */
    private fun createRightPattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(longArrayOf(0, 100, 150, 100, 150, 100), -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createWaveform(longArrayOf(0, 100, 150, 100, 150, 100), -1)
        }
    }

    /**
     * STRAIGHT: 1 long pulse (300ms pulse)
     */
    private fun createStraightPattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    /**
     * ARRIVE: 1 long + 2 short (500ms pulse, 100ms pause, 100ms pulse, 100ms pause, 100ms pulse)
     */
    private fun createArrivePattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(longArrayOf(0, 500, 100, 100, 100, 100), -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createWaveform(longArrayOf(0, 500, 100, 100, 100, 100), -1)
        }
    }

    /**
     * Stop any ongoing vibration.
     */
    fun stop() {
        if (hasVibrator()) {
            vibrator?.cancel()
        }
    }
}
