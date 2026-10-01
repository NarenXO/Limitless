package com.teamdexters.limitless.blind

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticVocabulary {
    fun play(context: Context, type: String) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val effect = when (type) {
            "LEFT" -> VibrationEffect.createWaveform(longArrayOf(0, 100, 150, 100), -1)
            "RIGHT" -> VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50, 100), -1)
            "STRAIGHT" -> VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            "DANGER" -> VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50, 50, 50, 50), -1)
            "SUCCESS" -> VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 300), -1)
            else -> VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}
