package com.teamdexters.limitless.hazel

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveTriggerManager @Inject constructor() {

    private val cooldowns = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 30L * 60L * 1000L // 30 minutes

    fun checkBatteryTrigger(batteryPercent: Int, isCharging: Boolean): String? {
        val now = System.currentTimeMillis()
        val lastTriggered = cooldowns["battery"] ?: 0L

        if (batteryPercent <= 15 && !isCharging && (now - lastTriggered > COOLDOWN_MS)) {
            cooldowns["battery"] = now
            val alertMessage = "Battery is at $batteryPercent%. Please connect your charger to keep visual navigation active."
            Log.d("LIMITLESS_TRACE", "ProactiveTriggerManager: $alertMessage")
            return alertMessage
        }
        return null
    }

    fun checkPersonaChangeTrigger(newPersona: String): String? {
        val now = System.currentTimeMillis()
        val lastTriggered = cooldowns["persona_$newPersona"] ?: 0L

        if (now - lastTriggered > COOLDOWN_MS) {
            cooldowns["persona_$newPersona"] = now
            val alertMessage = when (newPersona.lowercase()) {
                "blind", "blind assist" -> "Blind Assist active. Camera visual scanning is enabled."
                "deaf", "deaf assist", "hard of hearing" -> "Deaf Captions active. Live sound captions and vibration vocabulary are ready."
                "speech", "speech assist", "speech impaired" -> "Speech AAC active. Multilingual phrase cards ready."
                "mobility", "mobility assist", "wheelchair" -> "Mobility Nav active. Accessible building routes ready."
                else -> "New mode active."
            }
            Log.d("LIMITLESS_TRACE", "ProactiveTriggerManager: $alertMessage")
            return alertMessage
        }
        return null
    }
}
