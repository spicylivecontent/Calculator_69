package com.calculator.app.security

/**
 * Läser och skriver känsliga värden i SharedPreferences **krypterade** (AES/GCM med nyckel
 * i Android Keystore).
 *
 * Värden som sparades innan krypteringen fanns saknar prefixet `enc:` och läses som
 * klartext – de krypteras nästa gång de skrivs. Kan krypteringen inte användas alls skrivs
 * värdet som klartext i stället, så att appen aldrig slutar fungera.
 */
internal object SecureValue {

    /** Tolkar ett sparat värde. Null om det är tomt eller inte går att dekryptera. */
    fun read(raw: String?): String? = when {
        raw == null -> null
        CipherPayload.isEncrypted(raw) -> SecretCipher.decrypt(raw)
        else -> raw
    }

    /** Värdet som ska sparas, krypterat om det går. */
    fun write(plain: String): String = SecretCipher.encrypt(plain) ?: plain
}
