package com.calculator.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The vault must never silently choose an unencrypted database. */
class VaultDatabasePlanTest {

    @Test
    fun encryptedDatabaseIsUsedWhenEncryptionWorks() {
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = true,
            plainExists = false
        )

        assertEquals(VaultDatabasePlan.ENCRYPTED_NAME, layout.databaseName)
        assertNull("nothing to migrate on first use", layout.migrateFrom)
    }

    @Test
    fun oldUnencryptedDatabaseIsOnlySelectedAsMigrationSource() {
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = true,
            plainExists = true
        )

        assertEquals(VaultDatabasePlan.ENCRYPTED_NAME, layout.databaseName)
        assertEquals(VaultDatabasePlan.PLAIN_NAME, layout.migrateFrom)
    }

    @Test
    fun interruptedMigrationIsRetriedWhileThePlainSourceExists() {
        // The encrypted target may already exist; the old file remains a migration source
        // until the transaction marker is committed and the source can be deleted.
        val layout = VaultDatabasePlan.layout(
            encryptionAvailable = true,
            plainExists = true
        )

        assertEquals(VaultDatabasePlan.ENCRYPTED_NAME, layout.databaseName)
        assertEquals(VaultDatabasePlan.PLAIN_NAME, layout.migrateFrom)
    }

    @Test
    fun unavailableEncryptionFailsClosedAndDoesNotSelectPlaintext() {
        val error = runCatching {
            VaultDatabasePlan.layout(
                encryptionAvailable = false,
                plainExists = true
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("refusing plaintext"))
    }

    @Test
    fun databaseNamesStayDistinct() {
        assertTrue(VaultDatabasePlan.ENCRYPTED_NAME != VaultDatabasePlan.PLAIN_NAME)
        assertTrue(VaultDatabasePlan.ENCRYPTED_NAME.endsWith(".db"))
        assertTrue(VaultDatabasePlan.PLAIN_NAME.endsWith(".db"))
    }
}
