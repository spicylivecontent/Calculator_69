package com.calculator.app.security

import java.util.Base64

/**
 * Formatet för ett krypterat värde.
 *
 * Värdet sparas som `enc:` + Base64 av [IV:ns längd][IV][chiffertext] – prefixet gör att
 * äldre klartextvärden kan kännas igen och läsas som de är.
 *
 * Allt utom själva krypteringen (som kräver Android Keystore) ligger här, så att formatet
 * kan enhetstestas på vanlig JVM.
 */
internal object CipherPayload {

    const val PREFIX = "enc:"

    /** Rimligt tak för en IV – GCM använder 12 byte. */
    private const val MAX_IV_LENGTH = 32

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    fun markEncrypted(encoded: String): String = PREFIX + encoded

    /** Delen efter prefixet, eller null om värdet inte är krypterat. */
    fun encodedPart(value: String?): String? =
        value?.takeIf { it.startsWith(PREFIX) }?.substring(PREFIX.length)

    /** Packar ihop IV och chiffertext till ett värde som kan sparas. */
    fun encode(iv: ByteArray, data: ByteArray): String {
        val combined = ByteArray(1 + iv.size + data.size)
        combined[0] = iv.size.toByte()
        iv.copyInto(combined, destinationOffset = 1)
        data.copyInto(combined, destinationOffset = 1 + iv.size)
        return markEncrypted(Base64.getEncoder().encodeToString(combined))
    }

    /** Läser ut IV och chiffertext. Null om värdet är trasigt eller inte är krypterat. */
    fun decode(value: String?): Pair<ByteArray, ByteArray>? {
        val payload = encodedPart(value) ?: return null
        val combined = runCatching { Base64.getDecoder().decode(payload) }.getOrNull() ?: return null
        if (combined.size <= 1) return null

        val ivSize = combined[0].toInt()
        if (ivSize <= 0 || ivSize > MAX_IV_LENGTH) return null
        // Ett värde utan chiffertext är trasigt (till exempel avbruten skrivning) och
        // avvisas, så att det inte förväxlas med ett tomt men giltigt värde.
        if (combined.size <= 1 + ivSize) return null

        return combined.copyOfRange(1, 1 + ivSize) to combined.copyOfRange(1 + ivSize, combined.size)
    }
}
