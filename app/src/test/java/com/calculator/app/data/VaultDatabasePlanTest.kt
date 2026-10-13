package com.calculator.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Databasens filval är säkerhetskritiskt: väljer appen fel fil tappar användaren sitt
 * bibliotek, och kör migreringen två gånger blir allt dubbelt. Här vaktas hela tabellen.
 */
class VaultDatabasePlanTest {

    @Test
    fun encryptedDatabaseIsUsedWhenEncryptionWorks() {
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = true,
            encryptedExists = false,
            plainExists = false
        )

        assertTrue("appen ska använda den krypterade filen", layout.encrypted)
        assertEquals(VaultDatabasePlan.ENCRYPTED_NAME, layout.databaseName)
        assertNull("inget att flytta första gången", layout.migrateFrom)
    }

    @Test
    fun oldUnencryptedDatabaseIsMigratedOnce() {
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = true,
            encryptedExists = false,
            plainExists = true
        )

        assertEquals(VaultDatabasePlan.ENCRYPTED_NAME, layout.databaseName)
        assertEquals("gamla filen ska flyttas in", VaultDatabasePlan.PLAIN_NAME, layout.migrateFrom)
    }

    @Test
    fun migrationIsNeverRunTwice() {
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = true,
            encryptedExists = true,
            plainExists = true
        )

        assertEquals(VaultDatabasePlan.ENCRYPTED_NAME, layout.databaseName)
        assertNull("redan migrerad – ingen dubbelimport", layout.migrateFrom)
    }

    @Test
    fun plainDatabaseIsKeptWhenEncryptionIsUnavailable() {
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = false,
            encryptedExists = false,
            plainExists = true
        )

        assertFalse(layout.encrypted)
        assertEquals("appen får inte bli oanvändbar", VaultDatabasePlan.PLAIN_NAME, layout.databaseName)
        assertNull(layout.migrateFrom)
    }

    @Test
    fun encryptionUnavailableNeverPointsAtTheEncryptedFile() {
        // Kan SQLCipher inte laddas går den krypterade filen inte att öppna alls – då är
        // den gamla filen enda möjliga valet, även om en krypterad fil ligger kvar.
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = false,
            encryptedExists = true,
            plainExists = false
        )

        assertFalse(layout.encrypted)
        assertEquals(VaultDatabasePlan.PLAIN_NAME, layout.databaseName)
        assertNull(layout.migrateFrom)
    }

    @Test
    fun databaseNamesStayDistinct() {
        assertTrue(
            "filnamnen måste skilja sig, annars kan migreringen skriva över originalet",
            VaultDatabasePlan.ENCRYPTED_NAME != VaultDatabasePlan.PLAIN_NAME
        )
        assertTrue(VaultDatabasePlan.ENCRYPTED_NAME.endsWith(".db"))
        assertTrue(VaultDatabasePlan.PLAIN_NAME.endsWith(".db"))
    }
}
