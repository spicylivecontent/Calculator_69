package com.calculator.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Krypterar och dekrypterar enskilda värden med AES/GCM och en nyckel som ligger i
 * Android Keystore. Används för att skydda känsliga strängar i appens lagring
 * (sökhistorik, uppspelningshistorik och databasens lösenord).
 *
 * Nyckeln lämnar aldrig Keystore, och varje kryptering använder en slumpad IV som
 * sparas tillsammans med chiffertexten.
 */
object SecretCipher {

    private const val KEY_ALIAS = "com.calculator.app.secret_cipher_key"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    /** Prefix som visar att ett sparat värde är krypterat (äldre värden saknar det). */
    const val PREFIX = CipherPayload.PREFIX

    /** True om värdet redan är krypterat. */
    fun isEncrypted(value: String?): Boolean = CipherPayload.isEncrypted(value)

    /** Lägger på prefixet så att gamla klartextvärden kan kännas igen. */
    fun markEncrypted(encoded: String): String = CipherPayload.markEncrypted(encoded)

    /** Tar bort prefixet, eller null om värdet inte är krypterat. */
    fun encodedPart(value: String?): String? = CipherPayload.encodedPart(value)

    /** Krypterar en sträng. Null om krypteringen inte är möjlig. */
    fun encrypt(plainText: String): String? = encryptBytes(plainText.toByteArray(Charsets.UTF_8))

    /** Dekrypterar ett värde som skapats av [encrypt]. Null om det inte går. */
    fun decrypt(encoded: String?): String? = decryptBytes(encoded)?.toString(Charsets.UTF_8)

    fun encryptBytes(plain: ByteArray): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        CipherPayload.encode(cipher.iv, cipher.doFinal(plain))
    }.getOrNull()

    fun decryptBytes(encoded: String?): ByteArray? {
        val (iv, data) = CipherPayload.decode(encoded) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.doFinal(data)
        }.getOrNull()
    }

    @Synchronized
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
}
