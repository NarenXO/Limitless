package com.teamdexters.limitless.ui.blind

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.teamdexters.limitless.routing.model.TurnType

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
            TurnType.LEFT, TurnType.SLIGHT_LEFT -> createLeftPattern()
            TurnType.RIGHT, TurnType.SLIGHT_RIGHT -> createRightPattern()
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
     * LEFT: 2 short pulses (100ms pulse, 80ms pause, 100ms pulse)
     */
    private fun createLeftPattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
        }
    }

    /**
     * RIGHT: 3 short pulses (100ms pulse, 80ms pause, 100ms pulse, 80ms pause, 100ms pulse)
     */
    private fun createRightPattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100, 80, 100), -1)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100, 80, 100), -1)
        }
    }

    /**
     * STRAIGHT: 1 short pulse (150ms pulse)
     */
    private fun createStraightPattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    /**
     * ARRIVE: 1 long pulse (600ms pulse)
     */
    private fun createArrivePattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
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
