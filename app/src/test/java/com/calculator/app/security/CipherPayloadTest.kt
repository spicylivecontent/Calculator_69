package com.calculator.app.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Formatet för krypterade värden. Själva krypteringen kräver Android Keystore (och testas
 * därför på enhet), men inpackningen, prefixet och felhanteringen är ren logik – och det är
 * den som avgör om ett sparat värde kan läsas tillbaka eller inte.
 */
class CipherPayloadTest {

    private val iv = ByteArray(12) { it.toByte() }
    private val data = "blonde kitchen".toByteArray(Charsets.UTF_8)

    @Test
    fun encodedValueCanBeDecodedAgain() {
        val encoded = CipherPayload.encode(iv, data)

        assertTrue("värdet ska märkas som krypterat", encoded.startsWith(CipherPayload.PREFIX))
        val decoded = CipherPayload.decode(encoded)
        assertArrayEquals(iv, decoded!!.first)
        assertArrayEquals(data, decoded.second)
    }

    /**
     * Ett värde med IV men utan chiffertext (till exempel en avbruten skrivning) ska
     * avvisas i stället för att läsas som ett tomt värde. Testet nedan för trunkering
     * kontrollerar samma sak från andra hållet.
     */
    @Test
    fun aValueWithoutCipherTextIsRejected() {
        val encoded = CipherPayload.encode(iv, ByteArray(0))

        assertTrue("värdet ska fortfarande se krypterat ut", CipherPayload.isEncrypted(encoded))
        assertNull("men det går inte att läsa tillbaka", CipherPayload.decode(encoded))
    }

    @Test
    fun plainTextValuesAreNotTreatedAsEncrypted() {
        assertFalse(CipherPayload.isEncrypted("blonde"))
        assertFalse(CipherPayload.isEncrypted(null))
        assertNull("klartext ska inte kunna avkodas", CipherPayload.decode("blonde"))
        assertNull(CipherPayload.decode(null))
        assertNull(CipherPayload.encodedPart("blonde"))
    }

    @Test
    fun brokenValuesAreRejectedInsteadOfCrashing() {
        val samples = listOf(
            "enc:",
            "enc:!!!inte base64!!!",
            CipherPayload.PREFIX + java.util.Base64.getEncoder().encodeToString(byteArrayOf(0)),
            CipherPayload.PREFIX + java.util.Base64.getEncoder().encodeToString(ByteArray(4)),
            CipherPayload.PREFIX + java.util.Base64.getEncoder().encodeToString(byteArrayOf(40, 1, 2, 3))
        )

        for (sample in samples) {
            assertNull("borde avvisas: $sample", CipherPayload.decode(sample))
        }
    }

    @Test
    fun aValueTruncatedAfterTheIvIsRejected() {
        val encoded = CipherPayload.encode(iv, data)
        val combined = java.util.Base64.getDecoder().decode(CipherPayload.encodedPart(encoded)!!)

        // Behåll IV:n men kapa bort chiffertexten.
        val truncated = combined.copyOfRange(0, 1 + iv.size)

        assertNull(
            CipherPayload.decode(CipherPayload.markEncrypted(java.util.Base64.getEncoder().encodeToString(truncated)))
        )
    }

    @Test
    fun theRecordedIvLengthIsUsedOnDecode() {
        // Andra värden än GCM:s 12 byte ska också gå att läsa, så länge längden står först.
        val longIv = ByteArray(16) { (it + 1).toByte() }
        val encoded = CipherPayload.encode(longIv, data)

        val decoded = CipherPayload.decode(encoded)
        assertArrayEquals(longIv, decoded!!.first)
        assertArrayEquals(data, decoded.second)
    }
}
