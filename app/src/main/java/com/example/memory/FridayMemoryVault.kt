package com.example.memory

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted Local Personal Memory Store for FRIDAY.
 *
 * Guarantees:
 * - Local-only persistence using Android KeyStore AES-256 GCM encryption.
 * - Stores user facts, preferences, and remembered notes.
 * - Strictly rejects passwords, API keys, credentials, and payment tokens.
 */
class FridayMemoryVault(private val context: Context) {

    companion object {
        private const val TAG = "FridayMemoryVault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "FridayMemoryMasterKey"
        private const val PREFS_NAME = "friday_encrypted_memory_prefs"

        private val FORBIDDEN_WORDS = listOf(
            "password", "passwd", "pin", "api_key", "apikey", "secret_key",
            "token", "cvv", "credit_card", "debit_card", "auth_token", "private_key"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        initKeyStore()
    }

    private fun initKeyStore() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
                Log.i(TAG, "Generated fresh AES-256 GCM key in AndroidKeyStore.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing KeyStore: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed getting SecretKey", e)
            null
        }
    }

    private fun encrypt(plaintext: String): String? {
        val secretKey = getSecretKey() ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failure: ${e.message}")
            null
        }
    }

    private fun decrypt(base64Payload: String): String? {
        val secretKey = getSecretKey() ?: return null
        return try {
            val combined = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (combined.size < 12) return null
            val iv = ByteArray(12)
            val ciphertext = ByteArray(combined.size - 12)
            System.arraycopy(combined, 0, iv, 0, 12)
            System.arraycopy(combined, 12, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failure: ${e.message}")
            null
        }
    }

    /**
     * Checks if text contains sensitive security credentials that must NOT be stored.
     */
    fun isSensitiveCredential(text: String): Boolean {
        val lower = text.lowercase()
        return FORBIDDEN_WORDS.any { lower.contains(it) }
    }

    /**
     * Stores a memory safely.
     */
    fun remember(key: String, fact: String): Boolean {
        if (isSensitiveCredential(key) || isSensitiveCredential(fact)) {
            Log.w(TAG, "Blocked saving sensitive credentials to memory.")
            return false
        }
        val encrypted = encrypt(fact) ?: return false
        prefs.edit().putString(key.lowercase().trim(), encrypted).apply()
        Log.i(TAG, "Remembered fact under key: $key")
        return true
    }

    /**
     * Retrieves a single fact by key or query match.
     */
    fun recall(query: String): String? {
        val lower = query.lowercase().trim()
        val all = getAllMemories()
        // Exact match
        all[lower]?.let { return it }
        // Partial match
        val entry = all.entries.firstOrNull { lower.contains(it.key) || it.key.contains(lower) }
        return entry?.value
    }

    /**
     * Retrieves all saved user memories.
     */
    fun getAllMemories(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (value is String) {
                val decrypted = decrypt(value)
                if (decrypted != null) {
                    results[key] = decrypted
                }
            }
        }
        return results
    }

    /**
     * Deletes a remembered fact.
     */
    fun forget(keyOrQuery: String): Boolean {
        val lower = keyOrQuery.lowercase().trim()
        val allKeys = prefs.all.keys
        val match = allKeys.firstOrNull { it == lower || lower.contains(it) || it.contains(lower) }
        return if (match != null) {
            prefs.edit().remove(match).apply()
            Log.i(TAG, "Forgot fact under key: $match")
            true
        } else {
            false
        }
    }

    fun clearAll(): Boolean {
        prefs.edit().clear().apply()
        return true
    }
}
