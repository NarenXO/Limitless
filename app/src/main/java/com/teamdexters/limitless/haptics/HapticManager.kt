package com.teamdexters.limitless.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun playNameCallPattern() {
        val pattern = longArrayOf(0, 100, 50, 100, 50, 100)
        playPattern(pattern)
    }

    private fun playPattern(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    fun playDangerPattern() {
        val pattern = longArrayOf(0, 1500, 500, 1500)
        playPattern(pattern)
    }

    fun playDoorPattern() {
        val pattern = longArrayOf(0, 200, 100, 200)
        playPattern(pattern)
    }

    fun playApplausePattern() {
        val pattern = longArrayOf(0, 50, 50, 50, 50, 50, 50)
        playPattern(pattern)
    }

    fun playSOSLoopPattern() {
        val pattern = longArrayOf(0, 1000, 200, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Repeat from index 1 to keep looping [1000, 200, 1000, 200...]
            val effect = VibrationEffect.createWaveform(pattern, 1)
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 1)
        }
    }

    fun stop() {
        vibrator.cancel()
    }
}
