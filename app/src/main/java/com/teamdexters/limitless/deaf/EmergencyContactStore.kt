package com.teamdexters.limitless.deaf

import android.content.Context
import android.util.Log

/**
 * Singleton object for storing emergency contact information and speech settings.
 * Uses SharedPreferences for persistent storage.
 */
object EmergencyContactStore {
    
    private const val PREFS_NAME = "limitless_emergency_prefs"
    private const val KEY_CONTACT_NAME = "contact_name"
    private const val KEY_CONTACT_PHONE = "contact_phone"
    private const val KEY_SPEECH_SPEED = "speech_speed"
    private const val KEY_SPEECH_PITCH = "speech_pitch"
    
    private const val DEFAULT_NAME = "Emergency Contact"
    private const val DEFAULT_PHONE = "112"
    private const val DEFAULT_SPEED = 1.0f
    private const val DEFAULT_PITCH = 1.0f
    
    /**
     * Save emergency contact name and phone to SharedPreferences.
     */
    fun saveContact(context: Context, name: String, phone: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CONTACT_NAME, name)
            .putString(KEY_CONTACT_PHONE, phone)
            .apply()
        Log.d("LIMITLESS_TRACE", "EmergencyContactStore: Saved contact $name ($phone)")
    }
    
    /**
     * Load emergency contact name from SharedPreferences.
     */
    fun loadContactName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CONTACT_NAME, DEFAULT_NAME) ?: DEFAULT_NAME
    }
    
    /**
     * Load emergency contact phone from SharedPreferences.
     */
    fun loadContactPhone(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CONTACT_PHONE, DEFAULT_PHONE) ?: DEFAULT_PHONE
    }
    
    /**
     * Save speech speed to SharedPreferences.
     */
    fun saveSpeechSpeed(context: Context, speed: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_SPEECH_SPEED, speed)
            .apply()
        Log.d("LIMITLESS_TRACE", "EmergencyContactStore: Saved speech speed $speed")
    }
    
    /**
     * Load speech speed from SharedPreferences.
     */
    fun loadSpeechSpeed(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_SPEECH_SPEED, DEFAULT_SPEED)
    }
    
    /**
     * Save speech pitch to SharedPreferences.
     */
    fun saveSpeechPitch(context: Context, pitch: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_SPEECH_PITCH, pitch)
            .apply()
        Log.d("LIMITLESS_TRACE", "EmergencyContactStore: Saved speech pitch $pitch")
    }
    
    /**
     * Load speech pitch from SharedPreferences.
     */
    fun loadSpeechPitch(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_SPEECH_PITCH, DEFAULT_PITCH)
    }
}
