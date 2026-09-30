package com.teamdexters.limitless.deaf

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class representing a single emergency contact.
 */
data class EmergencyContact(
    val name: String,
    val phone: String,
    val priority: Int
)

/**
 * Singleton object for storing emergency contact information and speech settings.
 * Uses SharedPreferences for persistent storage.
 */
object EmergencyContactStore {
    
    private const val PREFS_NAME = "limitless_emergency_prefs"
    private const val KEY_CONTACT_NAME = "contact_name"
    private const val KEY_CONTACT_PHONE = "contact_phone"
    private const val KEY_CONTACTS_JSON = "limitless_emergency_contacts_json"
    private const val KEY_SPEECH_SPEED = "speech_speed"
    private const val KEY_SPEECH_PITCH = "speech_pitch"
    private const val KEY_SOS_CONSENT_GRANTED = "limitless_sos_consent_granted"
    
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
    
    /**
     * Set SOS consent status.
     */
    fun setSosConsent(context: Context, consented: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SOS_CONSENT_GRANTED, consented)
            .apply()
        Log.d("LIMITLESS_TRACE", "EmergencyContactStore: SOS consent set to $consented")
    }
    
    /**
     * Check if SOS consent has been granted.
     */
    fun hasSosConsent(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SOS_CONSENT_GRANTED, false)
    }
    
    /**
     * Save multiple emergency contacts to SharedPreferences as JSON.
     */
    fun saveContacts(context: Context, contacts: List<EmergencyContact>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        
        contacts.forEach { contact ->
            val jsonContact = JSONObject()
            jsonContact.put("name", contact.name)
            jsonContact.put("phone", contact.phone)
            jsonContact.put("priority", contact.priority)
            jsonArray.put(jsonContact)
        }
        
        prefs.edit()
            .putString(KEY_CONTACTS_JSON, jsonArray.toString())
            .apply()
        Log.d("LIMITLESS_TRACE", "EmergencyContactStore: Saved ${contacts.size} contacts")
    }
    
    /**
     * Load multiple emergency contacts from SharedPreferences JSON.
     * Returns default single contact if no contacts are saved.
     */
    fun getContacts(context: Context): List<EmergencyContact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_CONTACTS_JSON, null)
        
        if (jsonString.isNullOrEmpty()) {
            return listOf(EmergencyContact(DEFAULT_NAME, DEFAULT_PHONE, 1))
        }
        
        return try {
            val jsonArray = JSONArray(jsonString)
            val contacts = mutableListOf<EmergencyContact>()
            
            for (i in 0 until jsonArray.length()) {
                val jsonContact = jsonArray.getJSONObject(i)
                contacts.add(
                    EmergencyContact(
                        name = jsonContact.getString("name"),
                        phone = jsonContact.getString("phone"),
                        priority = jsonContact.getInt("priority")
                    )
                )
            }
            
            if (contacts.isEmpty()) {
                listOf(EmergencyContact(DEFAULT_NAME, DEFAULT_PHONE, 1))
            } else {
                contacts.sortedBy { it.priority }
            }
        } catch (e: Exception) {
            Log.e("LIMITLESS_TRACE", "EmergencyContactStore: Failed to parse contacts JSON", e)
            listOf(EmergencyContact(DEFAULT_NAME, DEFAULT_PHONE, 1))
        }
    }
}
