package com.example.mobile.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class BiometricCredentials(
    val email: String,
    val password: String,
    val displayName: String? = null
)

interface BiometricCredentialStore {
    fun isEnabled(): Boolean
    fun savedEmail(): String?
    fun savedDisplayName(): String?
    fun save(email: String, password: String, displayName: String? = null)
    fun read(): BiometricCredentials?
    fun clear()
}

class InMemoryBiometricCredentialStore : BiometricCredentialStore {
    private var stored: BiometricCredentials? = null

    override fun isEnabled(): Boolean = stored != null
    override fun savedEmail(): String? = stored?.email
    override fun savedDisplayName(): String? = stored?.displayName
    override fun save(email: String, password: String, displayName: String?) {
        stored = BiometricCredentials(email.trim(), password, displayName)
    }
    override fun read(): BiometricCredentials? = stored
    override fun clear() {
        stored = null
    }
}

class KeystoreBiometricCredentialStore(context: Context) : BiometricCredentialStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun isEnabled(): Boolean =
        prefs.getBoolean(KEY_ENABLED, false) &&
            !prefs.getString(KEY_CIPHERTEXT, null).isNullOrBlank() &&
            !prefs.getString(KEY_IV, null).isNullOrBlank()

    override fun savedEmail(): String? =
        if (isEnabled()) prefs.getString(KEY_EMAIL, null) else null

    override fun savedDisplayName(): String? =
        if (isEnabled()) prefs.getString(KEY_DISPLAY_NAME, null) else null

    override fun save(email: String, password: String, displayName: String?) {
        runCatching {
            val payload = json.encodeToString(
                BiometricCredentials(
                    email = email.trim(),
                    password = password,
                    displayName = displayName?.takeIf { it.isNotBlank() }
                )
            )
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            prefs.edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_EMAIL, email.trim())
                .putString(KEY_DISPLAY_NAME, displayName?.takeIf { it.isNotBlank() })
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        }.onFailure {
            clear()
        }
    }

    override fun read(): BiometricCredentials? {
        if (!isEnabled()) return null
        return runCatching {
            val encryptedB64 = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
            val ivB64 = prefs.getString(KEY_IV, null) ?: return null
            val encrypted = Base64.decode(encryptedB64, Base64.NO_WRAP)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(encrypted), Charsets.UTF_8)
            json.decodeFromString<BiometricCredentials>(plain)
        }.onFailure {
            clear()
        }.getOrNull()
    }

    override fun clear() {
        prefs.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_EMAIL)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_CIPHERTEXT)
            .remove(KEY_IV)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "biometric_auth"
        const val KEY_ALIAS = "rubank_biometric_creds_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ENABLED = "enabled"
        const val KEY_EMAIL = "email"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
    }
}
