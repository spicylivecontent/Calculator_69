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

/**
 * Vault-databas (SQLCipher). exportSchema=true så att framtida migrationer
 * kan genereras och granskas. Bump [version] och lägg till Migration när
 * schema ändras – undvik fallbackToDestructiveMigration i produktion.
 */
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
    exportSchema = true
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            check(SqlCipherLoader.load()) { "SQLCipher unavailable" }
            val passphrase = checkNotNull(VaultDatabaseKey.passphrase(context)) { "Vault key unavailable" }
            val plan = VaultDatabasePlan.layout(
                encryptionAvailable = true,
                plainExists = context.getDatabasePath(VaultDatabasePlan.PLAIN_NAME).exists()
            )
            layout = plan

            return Room.databaseBuilder(context, AppDatabase::class.java, plan.databaseName)
                .openHelperFactory(SupportOpenHelperFactory(passphrase, null, true))
                // När version > 1: .addMigrations(MIGRATION_1_2, …)
                .build()
        }

        suspend fun migrateLegacyIfNeeded(context: Context): Int? {
            val database = getInstance(context)
            val source = layout?.migrateFrom ?: return null
            val migrated = LegacyVaultMigrator.migrateIfNeeded(context.applicationContext, database, source)
            if (migrated != null) layout = layout?.copy(migrateFrom = null)
            return migrated
        }
    }
}
