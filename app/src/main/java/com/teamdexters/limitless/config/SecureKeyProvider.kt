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
        var key = sharedPreferences.getString("GEMINI_API_KEY", null)
        if (key.isNullOrEmpty()) {
            key = BuildConfig.GEMINI_API_KEY
        }
        android.util.Log.d("LIMITLESS_TRACE", "SecureKeyProvider: Key present length=${key?.length ?: 0}")
        return key
    }

    fun clearGeminiKey() {
        sharedPreferences.edit().remove("GEMINI_API_KEY").apply()
    }

    fun saveUserName(name: String) {
        sharedPreferences.edit().putString("USER_NAME", name).apply()
    }

    fun getUserName(): String? {
        return sharedPreferences.getString("USER_NAME", "Naren")
    }
}
