package com.calculator.app.security

import android.content.Context
import java.security.SecureRandom

/**
 * Lösenordet till den krypterade databasen.
 *
 * Lösenordet är 32 slumpade byte som skapas en gång och därefter förvaras **krypterat**
 * (AES/GCM med nyckel i Android Keystore) i appens privata inställningar. Det binds
 * därför inte till PIN-koden: byter användaren kod fortsätter databasen att fungera,
 * samtidigt som lösenordet aldrig ligger i klartext på disken.
 */
object VaultDatabaseKey {

    private const val PREFS_NAME = "calculator_vault_secret"
    private const val KEY_DATABASE_PASSPHRASE = "db_passphrase"
    private const val PASSPHRASE_BYTES = 32

    @Volatile
    private var cached: ByteArray? = null

    /** Hämtar (eller skapar) databasens lösenord. Null om Keystore inte kan användas. */
    fun passphrase(context: Context): ByteArray? {
        cached?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        SecretCipher.decryptBytes(prefs.getString(KEY_DATABASE_PASSPHRASE, null))?.let { existing ->
            if (existing.size >= 16) {
                cached = existing
                return existing
            }
        }

        val created = ByteArray(PASSPHRASE_BYTES).also { SecureRandom().nextBytes(it) }
        val wrapped = SecretCipher.encryptBytes(created) ?: return null
        prefs.edit().putString(KEY_DATABASE_PASSPHRASE, wrapped).apply()
        cached = created
        return created
    }

    /** True om ett lösenord redan finns (används av migreringen). */
    fun hasPassphrase(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_DATABASE_PASSPHRASE)
}
