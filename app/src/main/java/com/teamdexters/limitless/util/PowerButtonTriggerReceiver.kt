package com.teamdexters.limitless.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class PowerButtonTriggerReceiver : BroadcastReceiver() {

    private val pressTimestamps = mutableListOf<Long>()
    private val WINDOW_MILLIS = 2500L // 2.5 second window for 4 presses
    private val TARGET_PRESS_COUNT = 4

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_SCREEN_OFF || action == Intent.ACTION_SCREEN_ON) {
            val now = System.currentTimeMillis()
            pressTimestamps.add(now)

            // Remove timestamps older than WINDOW_MILLIS
            pressTimestamps.removeAll { now - it > WINDOW_MILLIS }

            Log.d("LIMITLESS_TRACE", "PowerButtonTrigger: Press count = ${pressTimestamps.size} in last $WINDOW_MILLIS ms")

            if (pressTimestamps.size >= TARGET_PRESS_COUNT) {
                pressTimestamps.clear()
                Log.d("LIMITLESS_TRACE", "PowerButtonTriggerReceiver: 4 Power Button presses detected!")

                // Vibrate 200ms
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))

                // Emit trigger to app
                PowerTriggerBus.emitTrigger()
            }
        }
    }
}
