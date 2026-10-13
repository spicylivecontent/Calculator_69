package com.calculator.app.data

/**
 * Chooses the encrypted vault database and whether a legacy plaintext file must be
 * migrated into it. Encryption is mandatory: the app must fail closed rather than
 * silently reopen the old plaintext database when SQLCipher or Keystore is unavailable.
 *
 * Kept free of Android dependencies so the file-selection policy can be unit-tested.
 */
internal object VaultDatabasePlan {

    /** Encrypted database (SQLCipher). */
    const val ENCRYPTED_NAME = "calculator_vault_enc.db"

    /** Legacy, unencrypted database. It is only ever opened as a migration source. */
    const val PLAIN_NAME = "calculator_vault.db"

    data class Layout(
        val databaseName: String,
        /** File to migrate from, or null when no migration is needed. */
        val migrateFrom: String?
    )

    /**
     * Refuses to select any database when encryption is unavailable. If a legacy file
     * remains, it is kept untouched until an encrypted migration can be completed.
     */
    fun layout(
        encryptionAvailable: Boolean,
        plainExists: Boolean
    ): Layout {
        check(encryptionAvailable) { "Vault encryption unavailable; refusing plaintext database" }
        return Layout(
            databaseName = ENCRYPTED_NAME,
            migrateFrom = PLAIN_NAME.takeIf { plainExists }
        )
    }
}
