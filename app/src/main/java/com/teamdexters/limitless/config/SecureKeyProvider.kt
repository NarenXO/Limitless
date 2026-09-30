package com.teamdexters.limitless.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.teamdexters.limitless.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveGeminiKey(key: String) {
        sharedPreferences.edit().putString("GEMINI_API_KEY", key).apply()
    }

    fun getGeminiKey(): String? {
        val key = sharedPreferences.getString("GEMINI_API_KEY", null)
        if (key.isNullOrEmpty() && BuildConfig.DEBUG) {
            val fallbackKey = BuildConfig.GEMINI_API_KEY
            if (fallbackKey.isNotEmpty() && fallbackKey != "YOUR_GEMINI_API_KEY_HERE" && fallbackKey != "null") {
                return fallbackKey
            }
        }
        return key
    }

    fun clearGeminiKey() {
        sharedPreferences.edit().remove("GEMINI_API_KEY").apply()
    }
}
