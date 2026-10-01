package com.teamdexters.limitless.deaf

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object VibrationVocabulary {
    fun play(context: Context, soundType: String) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = when(soundType) {
            "SIREN" -> longArrayOf(0, 500, 100, 200, 100, 500, 100, 200)
            "FIRE_ALARM" -> longArrayOf(0, 200, 100, 200, 100, 200)
            "DOORBELL" -> longArrayOf(0, 300, 100, 300)
            "DOG_BARKING" -> longArrayOf(0, 100, 50, 100, 50, 100, 50, 100)
            "BABY_CRYING" -> longArrayOf(0, 1000, 300)
            "CAR_HORN" -> longArrayOf(0, 800)
            else -> longArrayOf(0, 150)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}
