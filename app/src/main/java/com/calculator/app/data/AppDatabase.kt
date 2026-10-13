package com.calculator.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.calculator.app.data.dao.*
import com.calculator.app.data.entity.*
import com.calculator.app.security.SqlCipherLoader
import com.calculator.app.security.VaultDatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        VideoEntity::class,
        FavoriteEntity::class,
        WatchLaterEntity::class,
        DislikeRuleEntity::class,
        SearchPresetEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun videoDao(): VideoDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun dislikeRuleDao(): DislikeRuleDao
    abstract fun searchPresetDao(): SearchPresetDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var layout: VaultDatabasePlan.Layout? = null

        /**
         * Öppnar (och skapar vid behov) databasen.
         *
         * Är SQLCipher och Android Keystore tillgängliga krypteras hela filen med en
         * slumpad nyckel som förvaras krypterad i Keystore. Går det inte att få fram en
         * nyckel används i stället den gamla okrypterade filen, så att appen aldrig blir
         * oanvändbar på grund av krypteringen.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            val passphrase = if (SqlCipherLoader.load()) VaultDatabaseKey.passphrase(context) else null
            val plan = VaultDatabasePlan.layout(
                encryptionAvailable = passphrase != null,
                encryptedExists = context.getDatabasePath(VaultDatabasePlan.ENCRYPTED_NAME).exists(),
                plainExists = context.getDatabasePath(VaultDatabasePlan.PLAIN_NAME).exists()
            )
            layout = plan

            val builder = Room.databaseBuilder(context, AppDatabase::class.java, plan.databaseName)
            if (plan.encrypted && passphrase != null) {
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase, null, true))
            }
            return builder.build()
        }

        /**
         * Flyttar innehållet från den gamla okrypterade databasen första gången appen
         * startas efter uppdateringen. Returnerar antalet rader som flyttades.
         */
        suspend fun migrateLegacyIfNeeded(context: Context): Int? {
            val database = getInstance(context)
            val source = layout?.migrateFrom ?: return null
            val migrated = LegacyVaultMigrator.migrateIfNeeded(context.applicationContext, database, source)
            if (migrated != null) layout = layout?.copy(migrateFrom = null)
            return migrated
        }
    }
}
