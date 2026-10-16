package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePreferences(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("maya_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "MayaKeystoreSecretKey"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_KEY_ENCRYPTED_API_KEY = "encrypted_api_key"
        private const val PREF_KEY_API_KEY_IV = "api_key_iv"
        private const val PREF_KEY_USER_NAME = "user_name"
        private const val PREF_KEY_CURRENT_MODE = "current_mode"
        private const val PREF_KEY_VOICE_JARVIS = "voice_jarvis"
        private const val PREF_KEY_VOICE_GIRLFRIEND = "voice_girlfriend"
        private const val PREF_KEY_EXTRA_INSTRUCTIONS_JARVIS = "extra_instructions_jarvis"
        private const val PREF_KEY_EXTRA_INSTRUCTIONS_GIRLFRIEND = "extra_instructions_girlfriend"
        private const val PREF_KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val PREF_KEY_WAKE_WORD_MODEL = "wake_word_model"
        private const val PREF_KEY_WAKE_WORD_SENSITIVITY = "wake_word_sensitivity"
        private const val PREF_KEY_CONFIRM_ACTIONS = "confirm_sensitive_actions"
        private const val PREF_KEY_ANNOUNCE_MESSAGES = "announce_incoming_messages"
        private const val PREF_KEY_LIVE_SCREEN_MODE = "live_screen_mode"
        private const val PREF_KEY_ONBOARDING_DONE = "onboarding_done"

        @Volatile
        private var INSTANCE: SecurePreferences? = null

        fun getInstance(context: Context): SecurePreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = SecurePreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    init {
        initKeyStoreKey()
    }

    private fun initKeyStoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Fallback or handle smoothly
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            prefs.edit()
                .remove(PREF_KEY_ENCRYPTED_API_KEY)
                .remove(PREF_KEY_API_KEY_IV)
                .apply()
            return
        }

        try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

                prefs.edit()
                    .putString(PREF_KEY_ENCRYPTED_API_KEY, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString(PREF_KEY_API_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()
                return
            }
        } catch (_: Exception) { }

        // Plain fallback if KeyStore fails on some custom emulators
        prefs.edit()
            .putString(PREF_KEY_ENCRYPTED_API_KEY, Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
            .remove(PREF_KEY_API_KEY_IV)
            .apply()
    }

    fun getApiKey(): String {
        val encryptedBase64 = prefs.getString(PREF_KEY_ENCRYPTED_API_KEY, null) ?: return ""
        val ivBase64 = prefs.getString(PREF_KEY_API_KEY_IV, null)

        if (ivBase64 != null) {
            try {
                val secretKey = getSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
                    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decrypted = cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP))
                    return String(decrypted, Charsets.UTF_8)
                }
            } catch (_: Exception) { }
        }

        return try {
            String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun hasApiKey(): Boolean = getApiKey().isNotEmpty()

    var userName: String
        get() = prefs.getString(PREF_KEY_USER_NAME, "User") ?: "User"
        set(value) = prefs.edit().putString(PREF_KEY_USER_NAME, value).apply()

    var currentMode: String // "jarvis" or "girlfriend"
        get() = prefs.getString(PREF_KEY_CURRENT_MODE, "jarvis") ?: "jarvis"
        set(value) = prefs.edit().putString(PREF_KEY_CURRENT_MODE, value).apply()

    var voiceJarvis: String
        get() = prefs.getString(PREF_KEY_VOICE_JARVIS, "Charon") ?: "Charon"
        set(value) = prefs.edit().putString(PREF_KEY_VOICE_JARVIS, value).apply()

    var voiceGirlfriend: String
        get() = prefs.getString(PREF_KEY_VOICE_GIRLFRIEND, "Sulafat") ?: "Sulafat"
        set(value) = prefs.edit().putString(PREF_KEY_VOICE_GIRLFRIEND, value).apply()

    fun getActiveVoice(): String {
        return if (currentMode == "girlfriend") voiceGirlfriend else voiceJarvis
    }

    var extraInstructionsJarvis: String
        get() = prefs.getString(PREF_KEY_EXTRA_INSTRUCTIONS_JARVIS, "") ?: ""
        set(value) = prefs.edit().putString(PREF_KEY_EXTRA_INSTRUCTIONS_JARVIS, value).apply()

    var extraInstructionsGirlfriend: String
        get() = prefs.getString(PREF_KEY_EXTRA_INSTRUCTIONS_GIRLFRIEND, "") ?: ""
        set(value) = prefs.edit().putString(PREF_KEY_EXTRA_INSTRUCTIONS_GIRLFRIEND, value).apply()

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(PREF_KEY_WAKE_WORD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PREF_KEY_WAKE_WORD_ENABLED, value).apply()

    var wakeWordModel: String // "Hey Maya" or "Hey Jarvis"
        get() = prefs.getString(PREF_KEY_WAKE_WORD_MODEL, "Hey Maya") ?: "Hey Maya"
        set(value) = prefs.edit().putString(PREF_KEY_WAKE_WORD_MODEL, value).apply()

    var wakeWordSensitivity: String // "Low", "Medium", "High"
        get() = prefs.getString(PREF_KEY_WAKE_WORD_SENSITIVITY, "Medium") ?: "Medium"
        set(value) = prefs.edit().putString(PREF_KEY_WAKE_WORD_SENSITIVITY, value).apply()

    var askConfirmationBeforeActions: Boolean
        get() = prefs.getBoolean(PREF_KEY_CONFIRM_ACTIONS, true)
        set(value) = prefs.edit().putBoolean(PREF_KEY_CONFIRM_ACTIONS, value).apply()

    var announceIncomingMessages: Boolean
        get() = prefs.getBoolean(PREF_KEY_ANNOUNCE_MESSAGES, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_ANNOUNCE_MESSAGES, value).apply()

    var liveScreenMode: Boolean
        get() = prefs.getBoolean(PREF_KEY_LIVE_SCREEN_MODE, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_LIVE_SCREEN_MODE, value).apply()

    var isOnboardingDone: Boolean
        get() = prefs.getBoolean(PREF_KEY_ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(PREF_KEY_ONBOARDING_DONE, value).apply()

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}
