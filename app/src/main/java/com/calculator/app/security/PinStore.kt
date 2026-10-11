package com.calculator.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the vault PINs encrypted with an AES key kept by Android Keystore.
 * The key itself is never written to app preferences or included in the APK.
 */
class PinStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getRealPin(): String? = readPin(REAL_PIN_KEY)

    fun getDecoyPin(): String? = readPin(DECOY_PIN_KEY)

    fun savePins(realPin: String, decoyPin: String): Boolean = runCatching {
        val encryptedRealPin = encrypt(realPin)
        val encryptedDecoyPin = encrypt(decoyPin)
        preferences.edit()
            .putString(REAL_PIN_KEY, encryptedRealPin)
            .putString(DECOY_PIN_KEY, encryptedDecoyPin)
            .commit()
    }.getOrDefault(false)

    fun saveRealPin(pin: String): Boolean = savePin(REAL_PIN_KEY, pin)

    fun saveDecoyPin(pin: String): Boolean = savePin(DECOY_PIN_KEY, pin)

    private fun readPin(key: String): String? {
        val encrypted = preferences.getString(key, null) ?: return null
        return runCatching { decrypt(encrypted) }.getOrNull()
    }

    private fun savePin(key: String, pin: String): Boolean = runCatching {
        preferences.edit().putString(key, encrypt(pin)).commit()
    }.getOrDefault(false)

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteArray(1 + iv.size + ciphertext.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Invalid encrypted PIN" }

        val ivLength = payload[0].toInt() and 0xFF
        require(ivLength > 0 && payload.size > 1 + ivLength) { "Invalid encrypted PIN" }

        val iv = payload.copyOfRange(1, 1 + ivLength)
        val ciphertext = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "calculator_access"
        const val REAL_PIN_KEY = "real_pin_encrypted"
        const val DECOY_PIN_KEY = "decoy_pin_encrypted"
        const val KEY_ALIAS = "com.calculator.app.vault_pin_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
