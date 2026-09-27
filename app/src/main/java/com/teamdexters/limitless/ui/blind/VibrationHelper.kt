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
     * LEFT: two short pulses (100ms, 80ms gap, 100ms)
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
     * RIGHT: three short pulses (100ms each, 80ms gaps)
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
     * STRAIGHT: one short pulse (~150ms)
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
     * ARRIVE: one long pulse (~600ms)
     */
    private fun createArrivePattern(): VibrationEffect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
        } else {
            @Suppress("DEPRECATION")
            VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}
