package com.calculator.app.data

/**
 * Bestämmer vilken databasfil appen ska använda.
 *
 * Krypterad databas får ett eget filnamn (`calculator_vault_enc.db`) medan den gamla
 * okrypterade filen behåller sitt namn. Det gör att en uppgradering kan läsa in det
 * gamla innehållet, skriva det till den krypterade filen och därefter ta bort originalet –
 * och om krypteringen av någon anledning inte kan aktiveras fortsätter appen med den
 * gamla filen i stället för att tappa allt.
 *
 * Logiken är medvetet fri från Android-beroenden så att den kan enhetstestas.
 */
internal object VaultDatabasePlan {

    /** Krypterad databas (SQLCipher). */
    const val ENCRYPTED_NAME = "calculator_vault_enc.db"

    /** Äldre, okrypterad databas. */
    const val PLAIN_NAME = "calculator_vault.db"

    data class Layout(
        val encrypted: Boolean,
        val databaseName: String,
        /** Fil att flytta innehåll ifrån, eller null när ingen migrering behövs. */
        val migrateFrom: String?
    )

    /**
     * [encryptionAvailable] är false när SQLCipher-biblioteket eller Keystore inte kan
     * användas. Migreringen sker bara när den krypterade filen inte redan finns, så att
     * en redan genomförd flytt aldrig körs om.
     */
    fun layout(
        encryptionAvailable: Boolean,
        encryptedExists: Boolean,
        plainExists: Boolean
    ): Layout = when {
        !encryptionAvailable -> Layout(encrypted = false, databaseName = PLAIN_NAME, migrateFrom = null)
        plainExists && !encryptedExists -> Layout(true, ENCRYPTED_NAME, PLAIN_NAME)
        else -> Layout(encrypted = true, databaseName = ENCRYPTED_NAME, migrateFrom = null)
    }
}
